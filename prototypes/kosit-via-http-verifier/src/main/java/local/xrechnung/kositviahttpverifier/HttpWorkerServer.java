package local.xrechnung.kositviahttpverifier;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tools.jackson.databind.ObjectMapper;

final class HttpWorkerServer implements Closeable {

  private static final long DEFAULT_MAX_BODY_BYTES = 10L * 1024L * 1024L;

  private final HttpServer server;
  private final ExecutorService executor;
  private final ObjectMapper objectMapper;
  private final ValidationHandler validationHandler;
  private final String token;
  private final long maxBodyBytes;
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  private HttpWorkerServer(
      HttpServer server,
      ExecutorService executor,
      ObjectMapper objectMapper,
      ValidationHandler validationHandler,
      String token,
      long maxBodyBytes) {
    this.server = server;
    this.executor = executor;
    this.objectMapper = objectMapper;
    this.validationHandler = validationHandler;
    this.token = token;
    this.maxBodyBytes = maxBodyBytes;
  }

  static int run(String[] args) {
    ObjectMapper objectMapper = JsonSupport.createObjectMapper();
    try {
      WorkerArguments arguments = WorkerArguments.parse(args);
      ValidationService validationService = ValidationService.create(objectMapper);
      try (HttpWorkerServer worker = start(objectMapper, validationService, arguments)) {
        worker.writeReadyLine();
        worker.awaitShutdown();
      }
      return 0;
    } catch (Exception e) {
      System.err.println("Could not start kosit-via-http-verifier worker: " + summarize(e));
      return 1;
    }
  }

  static HttpWorkerServer start(
      ObjectMapper objectMapper,
      ValidationHandler validationHandler,
      WorkerArguments arguments) throws IOException {
    HttpServer server = HttpServer.create(
        new InetSocketAddress(arguments.host(), arguments.port()),
        0);
    ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
        new Thread(runnable, "kosit-http-worker-request"));
    HttpWorkerServer worker = new HttpWorkerServer(
        server,
        executor,
        objectMapper,
        validationHandler,
        arguments.token(),
        arguments.maxBodyBytes());
    server.createContext("/health", worker::handleHealth);
    server.createContext("/validate", worker::handleValidate);
    server.createContext("/shutdown", worker::handleShutdown);
    server.setExecutor(executor);
    server.start();
    return worker;
  }

  int port() {
    return server.getAddress().getPort();
  }

  void writeReadyLine() throws IOException {
    Map<String, Object> ready = new LinkedHashMap<String, Object>();
    ready.put("status", "ready");
    ready.put("host", server.getAddress().getHostString());
    ready.put("port", Integer.valueOf(port()));
    System.out.println(JsonSupport.toCompactJson(objectMapper, ready));
    System.out.flush();
  }

  void awaitShutdown() throws InterruptedException {
    shutdownLatch.await();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
    shutdownLatch.countDown();
  }

  private void handleHealth(HttpExchange exchange) throws IOException {
    if (!requireMethod(exchange, "GET") || !requireAuthorization(exchange)) {
      return;
    }
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put("status", "ok");
    response.put("port", Integer.valueOf(port()));
    sendJson(exchange, 200, response);
  }

  private void handleValidate(HttpExchange exchange) throws IOException {
    if (!requireMethod(exchange, "POST") || !requireAuthorization(exchange)) {
      return;
    }
    try {
      byte[] body = readBody(exchange);
      Map<String, Object> arguments = JsonSupport.fromJsonMap(objectMapper, body);
      sendJson(exchange, 200, validationHandler.validate(arguments));
    } catch (PayloadTooLargeException e) {
      sendJson(exchange, 413, error("request_too_large", e.getMessage()));
    } catch (IOException e) {
      sendJson(exchange, 400, error("request_invalid", "Could not parse JSON request."));
    }
  }

  private void handleShutdown(HttpExchange exchange) throws IOException {
    if (!requireMethod(exchange, "POST") || !requireAuthorization(exchange)) {
      return;
    }
    sendJson(exchange, 202, Map.of("status", "shutting_down"));
    Thread shutdownThread = new Thread(this::close, "kosit-http-worker-shutdown");
    shutdownThread.setDaemon(false);
    shutdownThread.start();
  }

  private boolean requireMethod(HttpExchange exchange, String expectedMethod) throws IOException {
    if (expectedMethod.equals(exchange.getRequestMethod())) {
      return true;
    }
    exchange.getResponseHeaders().set("Allow", expectedMethod);
    sendJson(exchange, 405, error("method_not_allowed", "Expected " + expectedMethod + "."));
    return false;
  }

  private boolean requireAuthorization(HttpExchange exchange) throws IOException {
    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
    if (("Bearer " + token).equals(authorization)) {
      return true;
    }
    sendJson(exchange, 401, error("unauthorized", "Missing or invalid bearer token."));
    return false;
  }

  private byte[] readBody(HttpExchange exchange) throws IOException, PayloadTooLargeException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0L;
    int read;
    while ((read = exchange.getRequestBody().read(chunk)) != -1) {
      total += read;
      if (total > maxBodyBytes) {
        throw new PayloadTooLargeException(
            "Request body exceeds " + maxBodyBytes + " bytes.");
      }
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
    byte[] bytes = JsonSupport.toJsonBytes(objectMapper, body);
    Headers headers = exchange.getResponseHeaders();
    headers.set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream responseBody = exchange.getResponseBody()) {
      responseBody.write(bytes);
    }
  }

  private static Map<String, Object> error(String category, String message) {
    Map<String, Object> error = new LinkedHashMap<String, Object>();
    error.put("category", category);
    error.put("message", message);
    return error;
  }

  private static String summarize(Exception error) {
    String message = error.getMessage();
    if (message != null && !message.trim().isEmpty()) {
      return message;
    }
    Throwable cause = error.getCause();
    if (cause != null && cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
      return cause.getMessage();
    }
    return error.getClass().getSimpleName();
  }

  static final class WorkerArguments {
    private final String token;
    private final String host;
    private final int port;
    private final long maxBodyBytes;

    private WorkerArguments(String token, String host, int port, long maxBodyBytes) {
      this.token = token;
      this.host = host;
      this.port = port;
      this.maxBodyBytes = maxBodyBytes;
    }

    static WorkerArguments parse(String[] args) {
      Map<String, String> values = new LinkedHashMap<String, String>();
      int index = 0;
      while (index < args.length) {
        String key = args[index];
        if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
          throw new IllegalArgumentException("Missing value for " + key);
        }
        values.put(key, args[index + 1]);
        index += 2;
      }
      String token = trimToNull(values.get("--token"));
      if (token == null) {
        throw new IllegalArgumentException("Missing required --token.");
      }
      return new WorkerArguments(
          token,
          values.getOrDefault("--host", "127.0.0.1"),
          parseInt(values.getOrDefault("--port", "0"), "--port"),
          parseLong(values.getOrDefault("--max-body-bytes", String.valueOf(DEFAULT_MAX_BODY_BYTES)),
              "--max-body-bytes"));
    }

    String token() {
      return token;
    }

    String host() {
      return host;
    }

    int port() {
      return port;
    }

    long maxBodyBytes() {
      return maxBodyBytes;
    }

    private static int parseInt(String value, String key) {
      try {
        int parsed = Integer.parseInt(value);
        if (parsed < 0 || parsed > 65535) {
          throw new NumberFormatException("port out of range");
        }
        return parsed;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(key + " must be a port between 0 and 65535.");
      }
    }

    private static long parseLong(String value, String key) {
      try {
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) {
          throw new NumberFormatException("not positive");
        }
        return parsed;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(key + " must be a positive byte count.");
      }
    }

    private static String trimToNull(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
  }

  private static final class PayloadTooLargeException extends Exception {
    private PayloadTooLargeException(String message) {
      super(message);
    }
  }
}

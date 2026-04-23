package local.xrechnung.kositviahttpverifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.ObjectMapper;

final class HttpWorkerProcess {

  private static final String HOST = "127.0.0.1";
  private static final int PORT = 0;
  private static final long DESTROY_WAIT_MILLIS = 5000L;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Path workerJar;
  private final Path workingDirectory;
  private final String token;

  private HttpWorkerProcess(Path workerJar, Path workingDirectory, String token) {
    this.workerJar = workerJar;
    this.workingDirectory = workingDirectory;
    this.token = token;
  }

  static HttpWorkerProcess forCurrentJar(Path workingDirectory) throws CliUsageException {
    Path codeSource;
    try {
      codeSource = PathsFromCodeSource.currentJarPath();
    } catch (IOException e) {
      throw new CliUsageException(e.getMessage());
    }
    return new HttpWorkerProcess(codeSource, workingDirectory, newToken());
  }

  RunningWorker start(
      ObjectMapper objectMapper,
      Duration startupTimeout,
      StderrCapture stderrCapture) throws VerificationException {
    ProcessBuilder builder = new ProcessBuilder(command());
    if (workingDirectory != null) {
      builder.directory(workingDirectory.toFile());
    }

    Process process = null;
    try {
      process = builder.start();
      startStderrReader(process, stderrCapture);
      URI baseUri = readReadyLine(objectMapper, process, startupTimeout, stderrCapture);
      return new RunningWorker(process, baseUri, token);
    } catch (VerificationException e) {
      destroyIfRunning(process);
      throw e;
    } catch (Exception e) {
      destroyIfRunning(process);
      throw new VerificationException(VerificationTechnicalFailure.of(
          "http_worker_start_failed",
          summarize(e),
          Map.of("workerJar", workerJar.toString()),
          stderrCapture.tail()),
          e);
    }
  }

  private static void destroyIfRunning(Process process) {
    if (process != null && process.isAlive()) {
      process.destroyForcibly();
      waitAfterDestroy(process);
    }
  }

  private static void waitAfterDestroy(Process process) {
    try {
      process.waitFor(DESTROY_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private List<String> command() {
    return List.of(
        "java",
        "-jar",
        workerJar.toString(),
        "--http-worker",
        "--token",
        token,
        "--host",
        HOST,
        "--port",
        String.valueOf(PORT));
  }

  private URI readReadyLine(
      ObjectMapper objectMapper,
      Process process,
      Duration startupTimeout,
      StderrCapture stderrCapture) throws Exception {
    BufferedReader stdout = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "kosit-http-worker-ready-reader");
      thread.setDaemon(true);
      return thread;
    });
    Future<String> readyLine = executor.submit(stdout::readLine);
    try {
      String line = readyLine.get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (line == null) {
        throw new VerificationException(VerificationTechnicalFailure.of(
            "http_worker_start_failed",
            "HTTP worker exited before reporting its port.",
            Map.of("exitValue", Integer.valueOf(exitValue(process))),
            stderrCapture.tail()));
      }
      Map<String, Object> ready = JsonSupport.fromJsonMap(objectMapper, line);
      if (!"ready".equals(String.valueOf(ready.get("status")))) {
        throw new IOException("Unexpected HTTP worker readiness line.");
      }
      int port = intValue(ready.get("port"));
      return new URI("http", null, HOST, port, null, null, null);
    } catch (TimeoutException e) {
      process.destroyForcibly();
      throw new VerificationException(VerificationTechnicalFailure.of(
          "http_worker_start_timeout",
          "HTTP worker did not become ready within " + startupTimeout.toMillis() + " ms.",
          Map.of("workerJar", workerJar.toString()),
          stderrCapture.tail()),
          e);
    } finally {
      executor.shutdownNow();
    }
  }

  private static void startStderrReader(Process process, StderrCapture stderrCapture) {
    Thread thread = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          stderrCapture.accept(line);
        }
      } catch (IOException ignored) {
      }
    }, "kosit-http-worker-stderr");
    thread.setDaemon(true);
    thread.start();
  }

  private static int intValue(Object value) throws IOException {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid HTTP worker port in readiness line.", e);
    }
  }

  private static int exitValue(Process process) {
    try {
      return process.exitValue();
    } catch (IllegalThreadStateException e) {
      return -1;
    }
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

  static final class RunningWorker {
    private final Process process;
    private final URI baseUri;
    private final String token;

    private RunningWorker(Process process, URI baseUri, String token) {
      this.process = process;
      this.baseUri = baseUri;
      this.token = token;
    }

    URI uri(String path) {
      return baseUri.resolve(path);
    }

    String bearerToken() {
      return token;
    }

    boolean awaitExit(Duration timeout) throws InterruptedException {
      return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    void destroyForcibly() {
      if (process.isAlive()) {
        process.destroyForcibly();
        waitAfterDestroy(process);
      }
    }
  }

  private static final class PathsFromCodeSource {
    private PathsFromCodeSource() {
    }

    private static Path currentJarPath() throws IOException {
      try {
        Path codeSource = Path.of(HttpWorkerProcess.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI())
            .toAbsolutePath()
            .normalize();
        if (!Files.isRegularFile(codeSource)) {
          throw new IOException(
              "Current code source is not a runnable JAR: " + codeSource
                  + ". Package the prototype before running it.");
        }
        return codeSource;
      } catch (URISyntaxException e) {
        throw new IOException("Could not resolve current JAR path.", e);
      }
    }
  }
}

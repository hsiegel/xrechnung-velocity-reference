package local.xrechnung.kositviahttpverifier;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import tools.jackson.databind.ObjectMapper;

public class HttpWorkerServerTest {

  private final ObjectMapper objectMapper = JsonSupport.createObjectMapper();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  public void healthRequiresBearerToken() throws Exception {
    try (HttpWorkerServer server = startServer(arguments -> Map.of())) {
      HttpResponse<String> response = httpClient.send(
          HttpRequest.newBuilder(uri(server, "/health")).GET().build(),
          HttpResponse.BodyHandlers.ofString());

      assertEquals(401, response.statusCode());
      assertEquals("unauthorized", JsonSupport.fromJsonMap(objectMapper, response.body()).get("category"));
    }
  }

  @Test
  public void validateAcceptsJsonAndReturnsHandlerResponse() throws Exception {
    AtomicReference<Map<String, Object>> capturedArguments =
        new AtomicReference<Map<String, Object>>();
    try (HttpWorkerServer server = startServer(arguments -> {
      capturedArguments.set(arguments);
      Map<String, Object> response = new LinkedHashMap<String, Object>();
      response.put("processingSuccessful", Boolean.TRUE);
      response.put("acceptRecommendation", "ACCEPT");
      return response;
    })) {
      HttpResponse<String> response = httpClient.send(
          post(server, "/validate", Map.of("xmlContent", "<Invoice/>")),
          HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("<Invoice/>", capturedArguments.get().get("xmlContent"));
      assertEquals("ACCEPT", JsonSupport.fromJsonMap(objectMapper, response.body()).get("acceptRecommendation"));
    }
  }

  @Test
  public void validateRejectsOversizedBody() throws Exception {
    try (HttpWorkerServer server = startServer(arguments -> Map.of(), 4L)) {
      HttpRequest request = HttpRequest.newBuilder(uri(server, "/validate"))
          .header("Authorization", "Bearer test-token")
          .header("Content-Type", "application/json; charset=utf-8")
          .POST(HttpRequest.BodyPublishers.ofString("{\"xmlContent\":\"too large\"}"))
          .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(413, response.statusCode());
      assertEquals("request_too_large", JsonSupport.fromJsonMap(objectMapper, response.body()).get("category"));
    }
  }

  private HttpWorkerServer startServer(ValidationHandler handler) throws Exception {
    return startServer(handler, 1024L);
  }

  private HttpWorkerServer startServer(ValidationHandler handler, long maxBodyBytes) throws Exception {
    try {
      return HttpWorkerServer.start(
          objectMapper,
          handler,
          HttpWorkerServer.WorkerArguments.parse(new String[] {
              "--token", "test-token",
              "--host", "127.0.0.1",
              "--port", "0",
              "--max-body-bytes", String.valueOf(maxBodyBytes)
          }));
    } catch (SocketException e) {
      Assume.assumeNoException("Local socket binding is unavailable in this sandbox.", e);
      throw e;
    }
  }

  private HttpRequest post(HttpWorkerServer server, String path, Map<String, Object> body) throws Exception {
    return HttpRequest.newBuilder(uri(server, path))
        .header("Authorization", "Bearer test-token")
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(JsonSupport.toCompactJson(objectMapper, body)))
        .build();
  }

  private static URI uri(HttpWorkerServer server, String path) {
    return URI.create("http://127.0.0.1:" + server.port() + path);
  }
}

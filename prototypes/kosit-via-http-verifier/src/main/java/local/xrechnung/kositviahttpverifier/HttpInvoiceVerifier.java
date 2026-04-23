package local.xrechnung.kositviahttpverifier;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class HttpInvoiceVerifier implements InvoiceVerifier, AutoCloseable {

  private final HttpWorkerProcess workerProcess;
  private final HttpTimeouts timeouts;
  private final ObjectMapper objectMapper;
  private final boolean includeRawServiceResult;
  private final HttpClient httpClient;
  private final StderrCapture stderrCapture = new StderrCapture(100);
  private HttpWorkerProcess.RunningWorker worker;
  private boolean closed;

  public HttpInvoiceVerifier(
      HttpWorkerProcess workerProcess,
      HttpTimeouts timeouts,
      ObjectMapper objectMapper,
      boolean includeRawServiceResult) {
    this.workerProcess = workerProcess;
    this.timeouts = timeouts;
    this.objectMapper = objectMapper;
    this.includeRawServiceResult = includeRawServiceResult;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(timeouts.getInitializationTimeout())
        .build();
  }

  @Override
  public synchronized VerificationResult verify(VerificationRequest request) throws VerificationException {
    HttpWorkerProcess.RunningWorker runningWorker = ensureWorker();
    try {
      String body = JsonSupport.toCompactJson(objectMapper, request.toToolArguments());
      HttpRequest httpRequest = HttpRequest.newBuilder(runningWorker.uri("/validate"))
          .timeout(timeouts.getValidationTimeout())
          .header("Authorization", "Bearer " + runningWorker.bearerToken())
          .header("Content-Type", "application/json; charset=utf-8")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw failure(
            "http_status_" + response.statusCode(),
            "HTTP worker returned status " + response.statusCode() + ".",
            Map.of("responseBody", response.body()));
      }
      Map<String, Object> serviceResult = JsonSupport.fromJsonMap(objectMapper, response.body());
      return new VerificationResultMapper(objectMapper, includeRawServiceResult)
          .fromStructuredContent(serviceResult);
    } catch (HttpTimeoutException e) {
      discardWorker(runningWorker);
      throw failure(
          "validation_timeout",
          "HTTP worker did not finish validation within "
              + timeouts.getValidationTimeout().toMillis()
              + " ms.",
          Map.of("timeoutMillis", Long.valueOf(timeouts.getValidationTimeout().toMillis())),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      discardWorker(runningWorker);
      throw failure(
          "http_transport_interrupted",
          summarize(e),
          Map.of("exceptionType", e.getClass().getName()),
          e);
    } catch (VerificationException e) {
      throw e;
    } catch (Exception e) {
      throw failure("http_transport_failed", summarize(e), Map.of("exceptionType", e.getClass().getName()), e);
    }
  }

  private void discardWorker(HttpWorkerProcess.RunningWorker runningWorker) {
    if (worker == runningWorker) {
      worker = null;
    }
    runningWorker.destroyForcibly();
  }

  @Override
  public synchronized void close() {
    closed = true;
    HttpWorkerProcess.RunningWorker runningWorker = worker;
    worker = null;
    if (runningWorker == null) {
      return;
    }
    try {
      HttpRequest request = HttpRequest.newBuilder(runningWorker.uri("/shutdown"))
          .timeout(timeouts.getInitializationTimeout())
          .header("Authorization", "Bearer " + runningWorker.bearerToken())
          .POST(HttpRequest.BodyPublishers.noBody())
          .build();
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      if (!runningWorker.awaitExit(timeouts.getInitializationTimeout())) {
        runningWorker.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      runningWorker.destroyForcibly();
    } catch (Exception e) {
      runningWorker.destroyForcibly();
    }
  }

  private HttpWorkerProcess.RunningWorker ensureWorker() throws VerificationException {
    if (closed) {
      throw failure(
          "http_worker_closed",
          "HTTP worker verifier is already closed.",
          Map.of());
    }
    if (worker == null) {
      worker = workerProcess.start(objectMapper, timeouts.getInitializationTimeout(), stderrCapture);
    }
    return worker;
  }

  private VerificationException failure(
      String category,
      String message,
      Map<String, Object> details) {
    return new VerificationException(VerificationTechnicalFailure.of(
        category,
        message,
        details,
        stderrCapture.tail()));
  }

  private VerificationException failure(
      String category,
      String message,
      Map<String, Object> details,
      Exception cause) {
    return new VerificationException(VerificationTechnicalFailure.of(
        category,
        message,
        details,
        stderrCapture.tail()),
        cause);
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
}

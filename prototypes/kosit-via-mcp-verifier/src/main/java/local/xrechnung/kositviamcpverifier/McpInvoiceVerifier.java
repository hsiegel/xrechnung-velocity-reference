package local.xrechnung.kositviamcpverifier;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class McpInvoiceVerifier implements InvoiceVerifier, AutoCloseable {

  static final String TOOL_NAME = "xrechnung_validate";

  private final McpServiceProcess serviceProcess;
  private final McpTimeouts timeouts;
  private final ObjectMapper objectMapper;
  private final boolean includeRawServiceResult;

  public McpInvoiceVerifier(
      McpServiceProcess serviceProcess,
      McpTimeouts timeouts,
      ObjectMapper objectMapper,
      boolean includeRawServiceResult) {
    this.serviceProcess = serviceProcess;
    this.timeouts = timeouts;
    this.objectMapper = objectMapper;
    this.includeRawServiceResult = includeRawServiceResult;
  }

  @Override
  public VerificationResult verify(VerificationRequest request) throws VerificationException {
    StderrCapture stderrCapture = new StderrCapture(100);
    McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapperSupplier().get();
    WorkingDirectoryStdioClientTransport transport = new WorkingDirectoryStdioClientTransport(
        serviceProcess.toServerParameters(),
        mcpJsonMapper,
        serviceProcess.getWorkingDirectory());
    transport.setStdErrorHandler(stderrCapture::accept);

    McpSyncClient client = null;
    try {
      client = McpClient.sync(transport)
          .clientInfo(new Implementation("kosit-via-mcp-verifier", implementationVersion()))
          .capabilities(ClientCapabilities.builder().build())
          .initializationTimeout(timeouts.getInitializationTimeout())
          .requestTimeout(timeouts.getRequestTimeout())
          .build();
      client.initialize();

      McpToolInvoker invoker = new McpToolInvoker(client, objectMapper);
      invoker.requireTool(TOOL_NAME, stderrCapture.tail());
      Map<String, Object> serviceResult =
          invoker.callTool(TOOL_NAME, request.toToolArguments(), stderrCapture.tail());
      return new VerificationResultMapper(objectMapper, includeRawServiceResult)
          .fromStructuredContent(serviceResult);
    } catch (VerificationException e) {
      throw e;
    } catch (Exception e) {
      throw new VerificationException(VerificationTechnicalFailure.of(
          "mcp_transport_failed",
          summarize(e),
          Map.of("exceptionType", e.getClass().getName()),
          stderrCapture.tail()),
          e);
    } finally {
      if (client != null) {
        try {
          client.closeGracefully();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @Override
  public void close() {
  }

  private static String implementationVersion() {
    Package pkg = McpInvoiceVerifier.class.getPackage();
    String version = pkg != null ? pkg.getImplementationVersion() : null;
    return version != null ? version : "1.0-SNAPSHOT";
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

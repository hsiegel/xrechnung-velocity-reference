package local.xrechnung.kositviamcpverifier;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class McpToolInvoker {

  private final McpSyncClient client;
  private final ObjectMapper objectMapper;

  McpToolInvoker(McpSyncClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  void requireTool(String toolName, String stderrTail) throws VerificationException {
    ListToolsResult tools = client.listTools();
    if (tools == null || tools.tools() == null) {
      throw failure("tool_list_failed", "MCP service did not return a tool list.", stderrTail);
    }
    for (Tool tool : tools.tools()) {
      if (toolName.equals(tool.name())) {
        return;
      }
    }
    throw failure("tool_missing", "MCP service does not expose tool " + toolName + ".", stderrTail);
  }

  Map<String, Object> callTool(
      String toolName,
      Map<String, Object> arguments,
      String stderrTail) throws VerificationException {
    CallToolRequest request = CallToolRequest.builder()
        .name(toolName)
        .arguments(new LinkedHashMap<String, Object>(arguments))
        .build();
    CallToolResult result = client.callTool(request);
    if (result == null || result.structuredContent() == null) {
      throw failure("tool_result_invalid", "MCP tool result has no structuredContent.", stderrTail);
    }
    return JsonSupport.toMap(objectMapper, result.structuredContent());
  }

  private VerificationException failure(
      String category,
      String message,
      String stderrTail) {
    return new VerificationException(VerificationTechnicalFailure.of(
        category,
        message,
        Map.of(),
        stderrTail));
  }
}

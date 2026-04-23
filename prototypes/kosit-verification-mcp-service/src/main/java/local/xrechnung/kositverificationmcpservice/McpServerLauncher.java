package local.xrechnung.kositverificationmcpservice;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import tools.jackson.databind.ObjectMapper;

final class McpServerLauncher implements Closeable {

  private static final String MODULE_DIR = "prototypes/kosit-verification-mcp-service";
  private static final String SERVER_NAME = "kosit-verification-mcp-service";
  private static final String TOOL_NAME = "xrechnung_validate";
  private static final String RESOURCE_TEMPLATE = "xrechnung-run://{runId}/{artifact}";

  private final McpSyncServer server;
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);
  private final String toolVersion;

  private McpServerLauncher(McpSyncServer server, String toolVersion) {
    this.server = server;
    this.toolVersion = toolVersion;
  }

  static McpServerLauncher start(ObjectMapper objectMapper) throws Exception {
    String toolVersion = implementationVersion();
    Path projectRoot = RepositoryLocator.findProjectRoot();
    Path moduleRoot = projectRoot.resolve(MODULE_DIR);
    Path workDir = moduleRoot.resolve("target/validator-work");
    Path runsDir = moduleRoot.resolve("target/runs");
    ValidatorConfigSupport.PreparedConfiguration configuration =
        ValidatorConfigSupport.prepare(projectRoot, workDir);
    ReusableValidator validator = ReusableValidator.create(configuration);
    RunArtifactStore artifactStore = new RunArtifactStore(runsDir, objectMapper);
    ValidationToolHandler toolHandler =
        new ValidationToolHandler(projectRoot, validator, artifactStore, toolVersion);
    McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapperSupplier().get();
    String inputSchemaJson = McpSchemaSupport.inputSchemaJson(objectMapper);
    String outputSchemaJson = McpSchemaSupport.outputSchemaJson(objectMapper);

    StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(mcpJsonMapper);
    SyncToolSpecification toolSpecification =
        toolHandler.toolSpecification(mcpJsonMapper, inputSchemaJson, outputSchemaJson);
    SyncResourceTemplateSpecification resourceSpecification = toolHandler.resourceTemplateSpecification();

    McpSyncServer server = McpServer.sync(transportProvider)
        .serverInfo(SERVER_NAME, toolVersion)
        .jsonMapper(mcpJsonMapper)
        .capabilities(ServerCapabilities.builder()
            .tools(true)
            .resources(false, false)
            .build())
        .tools(toolSpecification)
        .resourceTemplates(resourceSpecification)
        .build();

    return new McpServerLauncher(server, toolVersion);
  }

  String getToolVersion() {
    return toolVersion;
  }

  void awaitShutdown() throws InterruptedException {
    shutdownLatch.await();
  }

  @Override
  public void close() throws IOException {
    try {
      server.close();
    } finally {
      shutdownLatch.countDown();
    }
  }

  private static String implementationVersion() {
    Package pkg = McpServerLauncher.class.getPackage();
    String version = pkg != null ? pkg.getImplementationVersion() : null;
    return version != null ? version : "1.0-SNAPSHOT";
  }

  private static final class ValidationToolHandler {
    private final Path projectRoot;
    private final ReusableValidator validator;
    private final RunArtifactStore artifactStore;
    private final String toolVersion;

    private ValidationToolHandler(
        Path projectRoot,
        ReusableValidator validator,
        RunArtifactStore artifactStore,
        String toolVersion) {
      this.projectRoot = projectRoot;
      this.validator = validator;
      this.artifactStore = artifactStore;
      this.toolVersion = toolVersion;
    }

    private SyncToolSpecification toolSpecification(
        McpJsonMapper mcpJsonMapper,
        String inputSchemaJson,
        String outputSchemaJson) {
      return SyncToolSpecification.builder()
          .tool(Tool.builder()
              .name(TOOL_NAME)
              .description("Validate an XRechnung XML document with the embedded KoSIT validator.")
              .inputSchema(mcpJsonMapper, inputSchemaJson)
              .outputSchema(mcpJsonMapper, outputSchemaJson)
              .build())
          .callHandler((exchange, request) -> handleToolCall(request.arguments()))
          .build();
    }

    private SyncResourceTemplateSpecification resourceTemplateSpecification() {
      return new SyncResourceTemplateSpecification(
          ResourceTemplate.builder()
              .uriTemplate(RESOURCE_TEMPLATE)
              .name("XRechnung run artifact")
              .description("Read input.xml, result.json, or report.xml from a persisted validation run.")
              .mimeType("text/plain")
              .build(),
          (exchange, request) -> {
            try {
              return handleResourceRead(request.uri());
            } catch (ToolFailure e) {
              throw new IllegalArgumentException("Could not read resource: " + e.getMessage(), e);
            }
          });
    }

    private CallToolResult handleToolCall(Map<String, Object> arguments) {
      Map<String, Object> requestedInput = ValidationInputResolver.describeRequestedInput(arguments);
      RunArtifactStore.RunHandle runHandle = RunArtifactStore.RunHandle.notPersisted();

      try {
        ValidationInput input = ValidationInputResolver.resolve(projectRoot, arguments);
        runHandle = artifactStore.begin(input);
        InvoiceScopeInspector.ScopeCheck scopeCheck = InvoiceScopeInspector.inspect(input.getXmlBytes());
        if (!scopeCheck.isSupported()) {
          throw new ToolFailure(
              "scope_unsupported",
              scopeCheck.getMessage(),
              Map.of("customizationId", scopeCheck.getCustomizationId()));
        }

        ReusableValidator.ValidationRun validationRun =
            validator.validate(input.getXmlBytes(), input.getDocumentReference());
        runHandle = artifactStore.storeReport(runHandle, validationRun.getReportXmlBytes());
        KositReportParser.ReportAnalysis reportAnalysis =
            KositReportParser.analyze(validationRun.getReportXmlBytes());
        Map<String, Object> result = ValidationResultMapper.successEnvelope(
            toolVersion,
            input,
            scopeCheck,
            validationRun,
            reportAnalysis,
            artifactStore.artifactBlock(runHandle, validationRun.getReportXmlBytes() != null));
        runHandle = artifactStore.writeResult(runHandle, result);
        result.put("artifacts", artifactStore.artifactBlock(runHandle, validationRun.getReportXmlBytes() != null));
        if (runHandle.isPersisted()) {
          runHandle = artifactStore.writeResult(runHandle, result);
          result.put("artifacts", artifactStore.artifactBlock(runHandle, validationRun.getReportXmlBytes() != null));
        }
        return CallToolResult.builder()
            .content(List.of(new TextContent(summaryText(result))))
            .structuredContent(result)
            .build();
      } catch (ToolFailure e) {
        Map<String, Object> result = ValidationResultMapper.errorEnvelope(
            toolVersion,
            requestedInput,
            e,
            artifactStore.artifactBlock(runHandle, false));
        if (runHandle.isPersisted()) {
          try {
            runHandle = artifactStore.writeResult(runHandle, result);
            result.put("artifacts", artifactStore.artifactBlock(runHandle, false));
          } catch (ToolFailure ignored) {
          }
        }
        return CallToolResult.builder()
            .isError(true)
            .content(List.of(new TextContent("Validation tool error: " + e.getMessage())))
            .structuredContent(result)
            .build();
      } catch (Exception e) {
        ToolFailure failure = new ToolFailure(
            "unexpected",
            summarizeException(e),
            Map.of("exceptionType", e.getClass().getName()),
            e);
        Map<String, Object> result = ValidationResultMapper.errorEnvelope(
            toolVersion,
            requestedInput,
            failure,
            artifactStore.artifactBlock(runHandle, false));
        if (runHandle.isPersisted()) {
          try {
            runHandle = artifactStore.writeResult(runHandle, result);
            result.put("artifacts", artifactStore.artifactBlock(runHandle, false));
          } catch (ToolFailure ignored) {
          }
        }
        return CallToolResult.builder()
            .isError(true)
            .content(List.of(new TextContent("Unexpected validation error: " + summarizeException(e))))
            .structuredContent(result)
            .build();
      }
    }

    private ReadResourceResult handleResourceRead(String uri) throws ToolFailure {
      RunArtifactStore.ReadableArtifact artifact = artifactStore.readArtifact(uri);
      TextResourceContents contents = new TextResourceContents(
          artifact.getUri(),
          artifact.getMimeType(),
          artifact.getText());
      return new ReadResourceResult(List.of(contents));
    }

    private String summaryText(Map<String, Object> result) {
      Object acceptRecommendation = result.get("acceptRecommendation");
      Map<?, ?> summary = (Map<?, ?>) result.get("summary");
      Map<?, ?> artifacts = (Map<?, ?>) result.get("artifacts");
      Object total = summary != null ? summary.get("findingCountTotal") : null;
      Object runId = artifacts != null ? artifacts.get("runId") : null;
      StringBuilder builder = new StringBuilder();
      builder.append("Validation completed: ");
      builder.append(acceptRecommendation != null ? acceptRecommendation : "NO_DECISION");
      builder.append(", findings=").append(total != null ? total : 0);
      if (runId != null) {
        builder.append(", runId=").append(runId);
      }
      return builder.toString();
    }

    private String summarizeException(Exception error) {
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
}

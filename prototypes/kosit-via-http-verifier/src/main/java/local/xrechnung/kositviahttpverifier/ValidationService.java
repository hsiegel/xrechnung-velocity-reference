package local.xrechnung.kositviahttpverifier;

import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class ValidationService implements ValidationHandler {

  private static final String MODULE_DIR = "prototypes/kosit-via-http-verifier";

  private final Path projectRoot;
  private final ReusableValidator validator;
  private final RunArtifactStore artifactStore;
  private final String toolVersion;

  private ValidationService(
      Path projectRoot,
      ReusableValidator validator,
      RunArtifactStore artifactStore,
      String toolVersion) {
    this.projectRoot = projectRoot;
    this.validator = validator;
    this.artifactStore = artifactStore;
    this.toolVersion = toolVersion;
  }

  static ValidationService create(ObjectMapper objectMapper) throws Exception {
    String toolVersion = implementationVersion();
    Path projectRoot = RepositoryLocator.findProjectRoot();
    Path moduleRoot = projectRoot.resolve(MODULE_DIR);
    Path workDir = moduleRoot.resolve("target/validator-work");
    Path runsDir = moduleRoot.resolve("target/runs");
    ValidatorConfigSupport.PreparedConfiguration configuration =
        ValidatorConfigSupport.prepare(projectRoot, workDir);
    return new ValidationService(
        projectRoot,
        ReusableValidator.create(configuration),
        new RunArtifactStore(runsDir, objectMapper),
        toolVersion);
  }

  @Override
  public Map<String, Object> validate(Map<String, Object> arguments) {
    Map<String, Object> safeArguments = arguments != null ? arguments : Map.of();
    Map<String, Object> requestedInput = ValidationInputResolver.describeRequestedInput(safeArguments);
    RunArtifactStore.RunHandle runHandle = RunArtifactStore.RunHandle.notPersisted();

    try {
      ValidationInput input = ValidationInputResolver.resolve(projectRoot, safeArguments);
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
      return result;
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
      return result;
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
      return result;
    }
  }

  private static String implementationVersion() {
    Package pkg = ValidationService.class.getPackage();
    String version = pkg != null ? pkg.getImplementationVersion() : null;
    return version != null ? version : "1.0-SNAPSHOT";
  }

  private static String summarizeException(Exception error) {
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

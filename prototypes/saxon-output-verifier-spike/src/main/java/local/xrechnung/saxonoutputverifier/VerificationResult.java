package local.xrechnung.saxonoutputverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VerificationResult {

  private static final String SCHEMA_VERSION = "1.0.0";
  private static final String FALLBACK_TOOL_VERSION = "1.0-SNAPSHOT";

  private final String schemaVersion;
  private final String toolVersion;
  private final Profile profile;
  private final Input input;
  private final boolean processingSuccessful;
  private final VerificationVerdict verificationVerdict;
  private final Summary summary;
  private final List<VerifierFinding> findings;
  private final Artifacts artifacts;
  private final RawVerifier rawVerifier;
  private final ToolError toolError;

  VerificationResult(
      Profile profile,
      Input input,
      boolean processingSuccessful,
      VerificationVerdict verificationVerdict,
      Summary summary,
      List<VerifierFinding> findings,
      Artifacts artifacts,
      RawVerifier rawVerifier,
      ToolError toolError) {
    this.schemaVersion = SCHEMA_VERSION;
    this.toolVersion = resolveToolVersion();
    this.profile = profile;
    this.input = input;
    this.processingSuccessful = processingSuccessful;
    this.verificationVerdict = verificationVerdict;
    this.summary = summary;
    this.findings = Collections.unmodifiableList(new ArrayList<VerifierFinding>(findings));
    this.artifacts = artifacts;
    this.rawVerifier = rawVerifier;
    this.toolError = toolError;
  }

  String getSchemaVersion() {
    return schemaVersion;
  }

  String getToolVersion() {
    return toolVersion;
  }

  Profile getProfile() {
    return profile;
  }

  Input getInput() {
    return input;
  }

  boolean isProcessingSuccessful() {
    return processingSuccessful;
  }

  VerificationVerdict getVerificationVerdict() {
    return verificationVerdict;
  }

  Summary getSummary() {
    return summary;
  }

  List<VerifierFinding> getFindings() {
    return findings;
  }

  Artifacts getArtifacts() {
    return artifacts;
  }

  RawVerifier getRawVerifier() {
    return rawVerifier;
  }

  ToolError getToolError() {
    return toolError;
  }

  private static String resolveToolVersion() {
    Package pkg = SaxonOutputVerifierCli.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null) {
      return pkg.getImplementationVersion();
    }
    return FALLBACK_TOOL_VERSION;
  }

  static final class Profile {
    private final String id;
    private final String scenarioName;
    private final String rootLocalName;
    private final String rootNamespaceUri;
    private final String customizationId;
    private final String profileId;

    Profile(
        String id,
        String scenarioName,
        String rootLocalName,
        String rootNamespaceUri,
        String customizationId,
        String profileId) {
      this.id = id;
      this.scenarioName = scenarioName;
      this.rootLocalName = rootLocalName;
      this.rootNamespaceUri = rootNamespaceUri;
      this.customizationId = customizationId;
      this.profileId = profileId;
    }

    String getId() {
      return id;
    }

    String getScenarioName() {
      return scenarioName;
    }

    String getRootLocalName() {
      return rootLocalName;
    }

    String getRootNamespaceUri() {
      return rootNamespaceUri;
    }

    String getCustomizationId() {
      return customizationId;
    }

    String getProfileId() {
      return profileId;
    }
  }

  static final class Input {
    private final String source;
    private final String inputName;
    private final String resolvedPath;
    private final String sha256Base64;
    private final String workDir;

    Input(
        String source,
        String inputName,
        String resolvedPath,
        String sha256Base64,
        String workDir) {
      this.source = source;
      this.inputName = inputName;
      this.resolvedPath = resolvedPath;
      this.sha256Base64 = sha256Base64;
      this.workDir = workDir;
    }

    String getSource() {
      return source;
    }

    String getInputName() {
      return inputName;
    }

    String getResolvedPath() {
      return resolvedPath;
    }

    String getSha256Base64() {
      return sha256Base64;
    }

    String getWorkDir() {
      return workDir;
    }
  }

  static final class Summary {
    private final Boolean profileMatched;
    private final Boolean schemaValid;
    private final boolean schematronExecuted;
    private final Boolean schematronValid;
    private final int xsdFindingCount;
    private final int schematronFindingCount;
    private final int findingCountTotal;

    Summary(
        Boolean profileMatched,
        Boolean schemaValid,
        boolean schematronExecuted,
        Boolean schematronValid,
        int xsdFindingCount,
        int schematronFindingCount,
        int findingCountTotal) {
      this.profileMatched = profileMatched;
      this.schemaValid = schemaValid;
      this.schematronExecuted = schematronExecuted;
      this.schematronValid = schematronValid;
      this.xsdFindingCount = xsdFindingCount;
      this.schematronFindingCount = schematronFindingCount;
      this.findingCountTotal = findingCountTotal;
    }

    Boolean getProfileMatched() {
      return profileMatched;
    }

    Boolean getSchemaValid() {
      return schemaValid;
    }

    boolean isSchematronExecuted() {
      return schematronExecuted;
    }

    Boolean getSchematronValid() {
      return schematronValid;
    }

    int getXsdFindingCount() {
      return xsdFindingCount;
    }

    int getSchematronFindingCount() {
      return schematronFindingCount;
    }

    int getFindingCountTotal() {
      return findingCountTotal;
    }
  }

  static final class Artifacts {
    private final String resultJsonPath;
    private final String svrlDirectory;
    private final List<String> svrlFiles;

    Artifacts(String resultJsonPath, String svrlDirectory, List<String> svrlFiles) {
      this.resultJsonPath = resultJsonPath;
      this.svrlDirectory = svrlDirectory;
      this.svrlFiles = Collections.unmodifiableList(new ArrayList<String>(svrlFiles));
    }

    String getResultJsonPath() {
      return resultJsonPath;
    }

    String getSvrlDirectory() {
      return svrlDirectory;
    }

    List<String> getSvrlFiles() {
      return svrlFiles;
    }
  }

  static final class RawVerifier {
    private final String selectedProfileId;
    private final String selectedScenarioName;
    private final String matchExpression;
    private final Boolean matchExpressionResult;
    private final String xmlSchemaPath;
    private final List<VerifierFinding> xsdFindings;
    private final List<SvrlArtifact> svrlArtifacts;

    RawVerifier(
        String selectedProfileId,
        String selectedScenarioName,
        String matchExpression,
        Boolean matchExpressionResult,
        String xmlSchemaPath,
        List<VerifierFinding> xsdFindings,
        List<SvrlArtifact> svrlArtifacts) {
      this.selectedProfileId = selectedProfileId;
      this.selectedScenarioName = selectedScenarioName;
      this.matchExpression = matchExpression;
      this.matchExpressionResult = matchExpressionResult;
      this.xmlSchemaPath = xmlSchemaPath;
      this.xsdFindings = Collections.unmodifiableList(new ArrayList<VerifierFinding>(xsdFindings));
      this.svrlArtifacts = Collections.unmodifiableList(new ArrayList<SvrlArtifact>(svrlArtifacts));
    }

    String getSelectedProfileId() {
      return selectedProfileId;
    }

    String getSelectedScenarioName() {
      return selectedScenarioName;
    }

    String getMatchExpression() {
      return matchExpression;
    }

    Boolean getMatchExpressionResult() {
      return matchExpressionResult;
    }

    String getXmlSchemaPath() {
      return xmlSchemaPath;
    }

    List<VerifierFinding> getXsdFindings() {
      return xsdFindings;
    }

    List<SvrlArtifact> getSvrlArtifacts() {
      return svrlArtifacts;
    }
  }

  static final class SvrlArtifact {
    private final String resourceLocation;
    private final String stylesheet;
    private final String outputPath;
    private final long failedAsserts;
    private final long successfulReports;

    SvrlArtifact(
        String resourceLocation,
        String stylesheet,
        String outputPath,
        long failedAsserts,
        long successfulReports) {
      this.resourceLocation = resourceLocation;
      this.stylesheet = stylesheet;
      this.outputPath = outputPath;
      this.failedAsserts = failedAsserts;
      this.successfulReports = successfulReports;
    }

    String getResourceLocation() {
      return resourceLocation;
    }

    String getStylesheet() {
      return stylesheet;
    }

    String getOutputPath() {
      return outputPath;
    }

    long getFailedAsserts() {
      return failedAsserts;
    }

    long getSuccessfulReports() {
      return successfulReports;
    }
  }

  static final class ToolError {
    private final String category;
    private final String message;

    ToolError(String category, String message) {
      this.category = category;
      this.message = message;
    }

    String getCategory() {
      return category;
    }

    String getMessage() {
      return message;
    }
  }
}

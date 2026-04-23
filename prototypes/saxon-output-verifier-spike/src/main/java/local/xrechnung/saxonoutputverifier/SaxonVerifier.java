package local.xrechnung.saxonoutputverifier;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.xml.sax.SAXException;

final class SaxonVerifier {

  private SaxonVerifier() {
  }

  static VerificationResult verify(
      Path projectRoot,
      Path xmlPath,
      Path workDir,
      Path resultJsonPath,
      ExpectedProfile expectedProfile) {
    VerificationResult.Profile profileInfo = new VerificationResult.Profile(
        expectedProfile.getId(),
        expectedProfile.getScenarioName(),
        expectedProfile.getRootLocalName(),
        expectedProfile.getRootNamespaceUri(),
        expectedProfile.getCustomizationId(),
        expectedProfile.getProfileId());
    VerificationResult.Input inputInfo = createInput(xmlPath, workDir);

    ValidatorBundleSupport.PreparedConfiguration configuration;
    try {
      configuration = ValidatorBundleSupport.prepare(projectRoot, workDir);
    } catch (Exception e) {
      return toolError(
          profileInfo,
          inputInfo,
          resultJsonPath,
          "config_unavailable",
          summarize(e),
          null,
          null,
          null,
          null,
          Collections.<VerifierFinding>emptyList());
    }

    ScenarioDefinition scenario;
    try {
      ScenarioCatalog scenarioCatalog = ScenarioCatalog.load(configuration.getScenariosFile());
      scenario = scenarioCatalog.requireScenario(expectedProfile.getScenarioName());
    } catch (Exception e) {
      return toolError(
          profileInfo,
          inputInfo,
          resultJsonPath,
          "config_invalid",
          summarize(e),
          null,
          null,
          null,
          null,
          Collections.<VerifierFinding>emptyList());
    }

    try {
      List<String> scenarioMismatches = expectedProfile.validateScenarioDefinition(scenario);
      if (!scenarioMismatches.isEmpty()) {
        return toolError(
            profileInfo,
            inputInfo,
            resultJsonPath,
            "bundle_profile_mismatch",
            firstMessage(scenarioMismatches),
            scenario,
            null,
            null,
            null,
            Collections.<VerifierFinding>emptyList());
      }

      InvoiceInspector.DocumentInfo documentInfo;
      try {
        documentInfo = InvoiceInspector.inspect(xmlPath);
      } catch (SAXException e) {
        return toolError(
            profileInfo,
            inputInfo,
            resultJsonPath,
            "input_unreadable",
            summarize(e),
            scenario,
            null,
            null,
            null,
            Collections.<VerifierFinding>emptyList());
      }

      boolean expectedProfileMatch;
      try {
        expectedProfileMatch = ScenarioMatcher.matches(xmlPath, scenario);
      } catch (Exception e) {
        return toolError(
            profileInfo,
            inputInfo,
            resultJsonPath,
            "profile_match_failed",
            summarize(e),
            scenario,
            null,
            null,
            null,
            Collections.<VerifierFinding>emptyList());
      }
      ProfileGate.Result profileGate =
          ProfileGate.evaluate(documentInfo, expectedProfile, expectedProfileMatch);
      List<VerifierFinding> findings = toProfileFindings(profileGate);

      if (!profileGate.matches()) {
        return buildResult(
            profileInfo,
            inputInfo,
            resultJsonPath,
            VerificationVerdict.FAIL,
            true,
            new VerificationResult.Summary(
                Boolean.FALSE,
                null,
                false,
                null,
                0,
                0,
                findings.size()),
            findings,
            new VerificationResult.Artifacts(resultJsonPath.toString(), null, Collections.<String>emptyList()),
            new VerificationResult.RawVerifier(
                expectedProfile.getId(),
                scenario.getName(),
                scenario.getMatchExpression(),
                Boolean.valueOf(expectedProfileMatch),
                configuration.getConfigDirectory().resolve(scenario.getXmlSchemaLocation()).normalize().toString(),
                Collections.<VerifierFinding>emptyList(),
                Collections.<VerificationResult.SvrlArtifact>emptyList()),
            null);
      }

      SchemaValidationStage.Result schemaResult;
      try {
        schemaResult =
            SchemaValidationStage.validate(configuration.getConfigDirectory(), scenario, xmlPath);
      } catch (Exception e) {
        return toolError(
            profileInfo,
            inputInfo,
            resultJsonPath,
            "xsd_processing_failed",
            summarize(e),
            scenario,
            Boolean.TRUE,
            configuration.getConfigDirectory().resolve(scenario.getXmlSchemaLocation()).normalize(),
            null,
            findings);
      }
      findings.addAll(schemaResult.getFindings());
      if (!schemaResult.isValid()) {
        return buildResult(
            profileInfo,
            inputInfo,
            resultJsonPath,
            VerificationVerdict.FAIL,
            true,
            new VerificationResult.Summary(
                Boolean.TRUE,
                Boolean.FALSE,
                false,
                null,
                schemaResult.getFindings().size(),
                0,
                findings.size()),
            findings,
            new VerificationResult.Artifacts(resultJsonPath.toString(), null, Collections.<String>emptyList()),
            new VerificationResult.RawVerifier(
                expectedProfile.getId(),
                scenario.getName(),
                scenario.getMatchExpression(),
                Boolean.TRUE,
                schemaResult.getXmlSchema().toString(),
                schemaResult.getFindings(),
                Collections.<VerificationResult.SvrlArtifact>emptyList()),
            null);
      }

      Path svrlDir = workDir.resolve("svrl").resolve(stripExtension(xmlPath.getFileName().toString()));
      SaxonSchematronStage.StageRun stageRun;
      try {
        stageRun =
            SaxonSchematronStage.run(xmlPath, configuration.getConfigDirectory(), scenario, svrlDir);
      } catch (Exception e) {
        return toolError(
            profileInfo,
            inputInfo,
            resultJsonPath,
            "schematron_processing_failed",
            summarize(e),
            scenario,
            Boolean.TRUE,
            schemaResult.getXmlSchema(),
            null,
            findings);
      }
      findings.addAll(stageRun.getAllFindings());

      boolean schematronValid = stageRun.getTotalFailedAsserts() == 0;
      return buildResult(
          profileInfo,
          inputInfo,
          resultJsonPath,
          schematronValid ? VerificationVerdict.PASS : VerificationVerdict.FAIL,
          true,
          new VerificationResult.Summary(
              Boolean.TRUE,
              Boolean.TRUE,
              true,
              Boolean.valueOf(schematronValid),
              schemaResult.getFindings().size(),
              toInt(stageRun.getTotalFailedAsserts()),
              findings.size()),
          findings,
          new VerificationResult.Artifacts(
              resultJsonPath.toString(),
              stageRun.getOutputDirectory().toString(),
              stageRun.getOutputFiles()),
          new VerificationResult.RawVerifier(
              expectedProfile.getId(),
              scenario.getName(),
              scenario.getMatchExpression(),
              Boolean.TRUE,
              schemaResult.getXmlSchema().toString(),
              schemaResult.getFindings(),
              toArtifacts(stageRun)),
          null);
    } catch (Exception e) {
      return toolError(
          profileInfo,
          inputInfo,
          resultJsonPath,
          "unexpected",
          summarize(e),
          null,
          null,
          null,
          null,
          Collections.<VerifierFinding>emptyList());
    }
  }

  private static VerificationResult buildResult(
      VerificationResult.Profile profileInfo,
      VerificationResult.Input inputInfo,
      Path resultJsonPath,
      VerificationVerdict verificationVerdict,
      boolean processingSuccessful,
      VerificationResult.Summary summary,
      List<VerifierFinding> findings,
      VerificationResult.Artifacts artifacts,
      VerificationResult.RawVerifier rawVerifier,
      VerificationResult.ToolError toolError) {
    return new VerificationResult(
        profileInfo,
        inputInfo,
        processingSuccessful,
        verificationVerdict,
        summary,
        findings,
        artifacts,
        rawVerifier,
        toolError);
  }

  private static VerificationResult toolError(
      VerificationResult.Profile profileInfo,
      VerificationResult.Input inputInfo,
      Path resultJsonPath,
      String category,
      String message,
      ScenarioDefinition scenario,
      Boolean matchExpressionResult,
      Path xmlSchemaPath,
      List<VerificationResult.SvrlArtifact> svrlArtifacts,
      List<VerifierFinding> findings) {
    return buildResult(
        profileInfo,
        inputInfo,
        resultJsonPath,
        VerificationVerdict.ERROR,
        false,
        new VerificationResult.Summary(
            null,
            null,
            false,
            null,
            0,
            0,
            findings.size()),
        findings,
        new VerificationResult.Artifacts(resultJsonPath.toString(), null, Collections.<String>emptyList()),
        new VerificationResult.RawVerifier(
            profileInfo.getId(),
            scenario != null ? scenario.getName() : profileInfo.getScenarioName(),
            scenario != null ? scenario.getMatchExpression() : null,
            matchExpressionResult,
            xmlSchemaPath != null ? xmlSchemaPath.toString() : null,
            Collections.<VerifierFinding>emptyList(),
            svrlArtifacts != null ? svrlArtifacts : Collections.<VerificationResult.SvrlArtifact>emptyList()),
        new VerificationResult.ToolError(category, message));
  }

  private static VerificationResult.Input createInput(Path xmlPath, Path workDir) {
    return new VerificationResult.Input(
        "xmlPath",
        xmlPath.getFileName().toString(),
        xmlPath.toString(),
        sha256Base64(xmlPath),
        workDir.toString());
  }

  private static String sha256Base64(Path xmlPath) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(java.nio.file.Files.readAllBytes(xmlPath));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      return null;
    }
  }

  private static List<VerifierFinding> toProfileFindings(ProfileGate.Result profileGate) {
    List<VerifierFinding> findings = new ArrayList<VerifierFinding>();
    for (ProfileGate.Mismatch mismatch : profileGate.getMismatches()) {
      findings.add(new VerifierFinding(
          "custom",
          "error",
          mismatch.getMessage(),
          null,
          mismatch.getCode(),
          null,
          null,
          null,
          "profile-gate",
          null,
          null,
          "profile-gate"));
    }
    return findings;
  }

  private static List<VerificationResult.SvrlArtifact> toArtifacts(
      SaxonSchematronStage.StageRun stageRun) {
    List<VerificationResult.SvrlArtifact> artifacts =
        new ArrayList<VerificationResult.SvrlArtifact>();
    for (SaxonSchematronStage.SvrlResult result : stageRun.getResults()) {
      artifacts.add(new VerificationResult.SvrlArtifact(
          result.getResourceLocation(),
          result.getStylesheet().toString(),
          result.getOutputPath().toString(),
          result.getFailedAsserts(),
          result.getSuccessfulReports()));
    }
    return artifacts;
  }

  private static String firstMessage(List<String> messages) {
    return messages.isEmpty() ? "Unknown bundle/profile mismatch." : messages.get(0);
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  private static int toInt(long value) {
    return (int) value;
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

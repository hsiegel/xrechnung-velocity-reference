package local.xrechnung.saxonoutputverifier;

import java.io.PrintStream;
import java.nio.file.Path;

public final class SaxonOutputVerifierCli {

  private static final int EXIT_PASS = 0;
  private static final int EXIT_FAIL = 1;
  private static final int EXIT_ERROR = 2;

  private SaxonOutputVerifierCli() {
  }

  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);
    System.exit(exitCode);
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    CliArguments cliArguments;
    try {
      cliArguments = CliArguments.parse(args);
    } catch (IllegalArgumentException e) {
      printUsageError(err, e.getMessage());
      return EXIT_ERROR;
    }

    if (cliArguments.isHelp()) {
      out.print(CliArguments.usage());
      return EXIT_PASS;
    }

    try {
      Path projectRoot = RepositoryLocator.findProjectRoot();
      Path xmlPath = cliArguments.requireXmlPath();
      Path workDir = cliArguments.resolveWorkDir(projectRoot);
      Path resultJsonPath = cliArguments.resolveResultJsonPath(workDir, xmlPath);
      ExpectedProfile expectedProfile =
          ProfileRegistry.builtInProfiles().require(cliArguments.getProfileId());

      VerificationResult result =
          SaxonVerifier.verify(projectRoot, xmlPath, workDir, resultJsonPath, expectedProfile);
      ResultJsonWriter.write(resultJsonPath, result);
      printResult(out, result);
      return exitCodeFor(result.getVerificationVerdict());
    } catch (IllegalArgumentException e) {
      err.println("Error: " + e.getMessage());
      return EXIT_ERROR;
    } catch (Exception e) {
      err.println("Verifier failed: " + summarize(e));
      return EXIT_ERROR;
    }
  }

  private static void printUsageError(PrintStream err, String message) {
    err.println("Error: " + message);
    err.println();
    err.print(CliArguments.usage());
  }

  private static void printResult(PrintStream out, VerificationResult result) {
    out.println("Verification verdict: " + result.getVerificationVerdict());
    out.println("Profile: " + result.getProfile().getId());
    out.println("Scenario: " + result.getProfile().getScenarioName());
    out.println("Input XML: " + result.getInput().getResolvedPath());
    out.println("Processing successful: " + yesNo(result.isProcessingSuccessful()));
    out.println("Profile matched: " + yesNoOrSkipped(result.getSummary().getProfileMatched()));
    out.println("Schema valid: " + yesNoOrSkipped(result.getSummary().getSchemaValid()));
    out.println("Schematron executed: " + yesNo(result.getSummary().isSchematronExecuted()));
    out.println("Schematron valid: " + yesNoOrSkipped(result.getSummary().getSchematronValid()));
    out.println("XSD findings: " + result.getSummary().getXsdFindingCount());
    out.println("Schematron findings: " + result.getSummary().getSchematronFindingCount());
    out.println("Total findings: " + result.getSummary().getFindingCountTotal());
    out.println("Result JSON: " + result.getArtifacts().getResultJsonPath());
    if (result.getArtifacts().getSvrlDirectory() != null) {
      out.println("SVRL directory: " + result.getArtifacts().getSvrlDirectory());
    }
    if (result.getToolError() != null) {
      out.println("Tool error: " + result.getToolError().getCategory()
          + " - " + result.getToolError().getMessage());
    }
    for (VerifierFinding finding : result.getFindings()) {
      out.println(
          "Finding [" + finding.getChannel() + "/" + finding.getSeverity() + "]: "
              + finding.getMessage());
    }
  }

  private static int exitCodeFor(VerificationVerdict verificationVerdict) {
    if (verificationVerdict == VerificationVerdict.PASS) {
      return EXIT_PASS;
    }
    if (verificationVerdict == VerificationVerdict.FAIL) {
      return EXIT_FAIL;
    }
    return EXIT_ERROR;
  }

  private static String yesNo(boolean value) {
    return value ? "yes" : "no";
  }

  private static String yesNoOrSkipped(Boolean value) {
    if (value == null) {
      return "skipped";
    }
    return yesNo(value.booleanValue());
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

package local.xrechnung.kositembeddedverifier;

import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.api.XmlError;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.oclc.purl.dsdl.svrl.FailedAssert;
import org.oclc.purl.dsdl.svrl.Text;

public final class KositEmbeddedVerifierCli {

  private static final int EXIT_OK = 0;
  private static final int EXIT_REJECTED = 1;
  private static final int EXIT_ERROR = 2;
  private static final int MAX_DETAIL_LINES = 5;
  private static final String SIMPLE_LOG_LEVEL_PROPERTY =
      "org.slf4j.simpleLogger.defaultLogLevel";

  private KositEmbeddedVerifierCli() {
  }

  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);
    System.exit(exitCode);
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    configureLoggingDefaults();

    CliArguments cliArguments;
    try {
      cliArguments = CliArguments.parse(args);
    } catch (IllegalArgumentException e) {
      printUsageError(err, e.getMessage());
      return EXIT_ERROR;
    }

    if (cliArguments.isHelp()) {
      out.print(CliArguments.usage());
      return EXIT_OK;
    }

    try {
      Path projectRoot = RepositoryLocator.findProjectRoot();
      Path xmlPath = cliArguments.requireXmlPath();
      byte[] xmlBytes = Files.readAllBytes(xmlPath);
      InvoiceScopeInspector.ScopeCheck scopeCheck = InvoiceScopeInspector.inspect(xmlBytes);

      if (!scopeCheck.isSupported()) {
        err.println("Scope error: " + scopeCheck.getMessage());
        return EXIT_ERROR;
      }
      if (scopeCheck.getMessage() != null) {
        out.println("Scope note: " + scopeCheck.getMessage());
      }

      Path reportDir = cliArguments.resolveReportDir(xmlPath);
      Path workDir = cliArguments.resolveWorkDir(projectRoot);
      ValidatorConfigSupport.PreparedConfiguration configuration =
          ValidatorConfigSupport.prepare(projectRoot, workDir);
      EmbeddedValidator.ValidationRun validation =
          EmbeddedValidator.validate(xmlBytes, xmlPath, reportDir, configuration);

      printSummary(out, xmlPath, scopeCheck, configuration, validation);
      return validation.isAccepted() ? EXIT_OK : EXIT_REJECTED;
    } catch (IllegalArgumentException e) {
      err.println("Error: " + e.getMessage());
      return EXIT_ERROR;
    } catch (Exception e) {
      err.println("Validation failed: " + summarize(e));
      return EXIT_ERROR;
    }
  }

  private static void printUsageError(PrintStream err, String message) {
    err.println("Error: " + message);
    err.println();
    err.print(CliArguments.usage());
  }

  private static void configureLoggingDefaults() {
    if (System.getProperty(SIMPLE_LOG_LEVEL_PROPERTY) == null) {
      System.setProperty(SIMPLE_LOG_LEVEL_PROPERTY, "warn");
    }
  }

  private static void printSummary(
      PrintStream out,
      Path xmlPath,
      InvoiceScopeInspector.ScopeCheck scopeCheck,
      ValidatorConfigSupport.PreparedConfiguration configuration,
      EmbeddedValidator.ValidationRun validation) {
    Result result = validation.getResult();

    out.println("Validated XML: " + xmlPath.toAbsolutePath());
    out.println("Configuration ZIP: " + configuration.getConfigZip());
    out.println("Configuration directory: " + configuration.getConfigDirectory());
    out.println("Scenarios file: " + configuration.getScenariosFile());
    if (scopeCheck.getCustomizationId() != null) {
      out.println("CustomizationID: " + scopeCheck.getCustomizationId());
    }
    out.println("Validation result: " + (validation.isAccepted() ? "ACCEPTED" : "REJECTED"));
    out.println("Accept recommendation: " + result.getAcceptRecommendation());
    out.println("Processing successful: " + yesNo(result.isProcessingSuccessful()));
    out.println("Well-formed: " + yesNo(result.isWellformed()));
    out.println("Schema valid: " + yesNo(result.isSchemaValid()));
    out.println("Schematron valid: " + yesNo(result.isSchematronValid()));
    out.println("Schema violations: " + result.getSchemaViolations().size());
    out.println("Failed asserts: " + result.getFailedAsserts().size());
    out.println("Report XML: " + describeReportPath(validation.getReportPath()));

    printProcessingErrors(out, result.getProcessingErrors());
    printSchemaViolations(out, result.getSchemaViolations());
    printFailedAsserts(out, result.getFailedAsserts());
  }

  private static void printProcessingErrors(PrintStream out, List<String> processingErrors) {
    if (processingErrors.isEmpty()) {
      return;
    }
    out.println("Processing errors:");
    for (String processingError : processingErrors) {
      out.println(" - " + processingError);
    }
  }

  private static void printSchemaViolations(PrintStream out, List<XmlError> schemaViolations) {
    if (schemaViolations.isEmpty()) {
      return;
    }
    out.println("Schema violation details:");
    for (int index = 0; index < schemaViolations.size() && index < MAX_DETAIL_LINES; index++) {
      out.println(" - " + formatXmlError(schemaViolations.get(index)));
    }
    if (schemaViolations.size() > MAX_DETAIL_LINES) {
      out.println(" - ...");
    }
  }

  private static void printFailedAsserts(PrintStream out, List<FailedAssert> failedAsserts) {
    if (failedAsserts.isEmpty()) {
      return;
    }
    out.println("Schematron failed assert details:");
    for (int index = 0; index < failedAsserts.size() && index < MAX_DETAIL_LINES; index++) {
      out.println(" - " + formatFailedAssert(failedAsserts.get(index)));
    }
    if (failedAsserts.size() > MAX_DETAIL_LINES) {
      out.println(" - ...");
    }
  }

  private static String formatXmlError(XmlError error) {
    StringBuilder builder = new StringBuilder();
    builder.append(error.getSeverity());
    if (error.getRowNumber() != null) {
      builder.append(" at line ").append(error.getRowNumber());
      if (error.getColumnNumber() != null) {
        builder.append(", column ").append(error.getColumnNumber());
      }
    }
    if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
      builder.append(": ").append(error.getMessage().trim());
    }
    return builder.toString();
  }

  private static String formatFailedAssert(FailedAssert failedAssert) {
    StringBuilder builder = new StringBuilder();
    if (failedAssert.getId() != null && !failedAssert.getId().isEmpty()) {
      builder.append(failedAssert.getId());
    } else {
      builder.append("failed-assert");
    }
    if (failedAssert.getFlag() != null && !failedAssert.getFlag().isEmpty()) {
      builder.append(" [").append(failedAssert.getFlag()).append("]");
    }
    String text = flattenText(failedAssert.getText());
    if (!text.isEmpty()) {
      builder.append(": ").append(text);
    }
    if (failedAssert.getLocation() != null && !failedAssert.getLocation().isEmpty()) {
      builder.append(" @ ").append(failedAssert.getLocation());
    }
    return builder.toString();
  }

  private static String flattenText(Text text) {
    if (text == null || text.getContent() == null || text.getContent().isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (Object part : text.getContent()) {
      String chunk = String.valueOf(part).trim();
      if (!chunk.isEmpty()) {
        if (builder.length() > 0) {
          builder.append(' ');
        }
        builder.append(chunk);
      }
    }
    return builder.toString();
  }

  private static String describeReportPath(Path reportPath) {
    return reportPath != null ? reportPath.toAbsolutePath().toString() : "not written";
  }

  private static String yesNo(boolean value) {
    return value ? "yes" : "no";
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

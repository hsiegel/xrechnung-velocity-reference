package local.xrechnung.kositviahttpverifier;

import java.io.IOException;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;

public final class KositViaHttpVerifierCli {

  private static final int EXIT_ACCEPTED = 0;
  private static final int EXIT_REJECTED = 1;
  private static final int EXIT_TECHNICAL_FAILURE = 2;
  private static final int EXIT_USAGE = 64;
  private static final String WORKER_FLAG = "--http-worker";
  private static final String SLF4J_LOG_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

  private KositViaHttpVerifierCli() {
  }

  public static void main(String[] args) {
    System.exit(run(args));
  }

  static int run(String[] args) {
    configureLoggingDefaults();
    if (args.length > 0 && WORKER_FLAG.equals(args[0])) {
      return HttpWorkerServer.run(Arrays.copyOfRange(args, 1, args.length));
    }

    ObjectMapper objectMapper = JsonSupport.createObjectMapper();
    try {
      CliArguments arguments = CliArguments.parse(args);
      try (HttpInvoiceVerifier verifier = new HttpInvoiceVerifier(
          arguments.toWorkerProcess(),
          arguments.toTimeouts(),
          objectMapper,
          arguments.isPrintRaw())) {
        VerificationResult result = verifier.verify(arguments.toVerificationRequest());
        printResult(objectMapper, result);
        return exitCode(result);
      }
    } catch (CliUsageException e) {
      System.err.println(e.getMessage());
      System.err.println(CliArguments.usage());
      return EXIT_USAGE;
    } catch (VerificationException e) {
      VerificationResult result = VerificationResult.technicalFailure(e.getFailure());
      printResult(objectMapper, result);
      return EXIT_TECHNICAL_FAILURE;
    }
  }

  private static int exitCode(VerificationResult result) {
    if (result.isTechnicalFailure()) {
      return EXIT_TECHNICAL_FAILURE;
    }
    return result.isAccepted() ? EXIT_ACCEPTED : EXIT_REJECTED;
  }

  private static void printResult(ObjectMapper objectMapper, VerificationResult result) {
    System.out.println(statusLine(result));
    try {
      System.out.println(JsonSupport.toCompactJson(objectMapper, result));
    } catch (IOException e) {
      System.out.println("{\"processingSuccessful\":false,\"accepted\":false,"
          + "\"technicalFailure\":{\"category\":\"result_serialization_failed\","
          + "\"message\":\"" + escape(e.getMessage()) + "\"}}");
    }
  }

  private static String statusLine(VerificationResult result) {
    if (result.isTechnicalFailure()) {
      return "TECHNICAL_FAILURE";
    }
    return result.isAccepted() ? "ACCEPTED" : "REJECTED";
  }

  private static void configureLoggingDefaults() {
    if (System.getProperty(SLF4J_LOG_LEVEL_PROPERTY) == null) {
      System.setProperty(SLF4J_LOG_LEVEL_PROPERTY, "error");
    }
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

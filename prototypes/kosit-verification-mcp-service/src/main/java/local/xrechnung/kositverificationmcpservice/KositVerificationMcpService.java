package local.xrechnung.kositverificationmcpservice;

import tools.jackson.databind.ObjectMapper;

public final class KositVerificationMcpService {

  private static final String SIMPLE_LOG_LEVEL_PROPERTY =
      "org.slf4j.simpleLogger.defaultLogLevel";

  private KositVerificationMcpService() {
  }

  public static void main(String[] args) {
    configureLoggingDefaults();

    try {
      ObjectMapper objectMapper = JsonSupport.createObjectMapper();
      final McpServerLauncher launcher = McpServerLauncher.start(objectMapper);
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            launcher.close();
          } catch (Exception ignored) {
          }
        }
      }, "kosit-verification-mcp-service-shutdown"));
      launcher.awaitShutdown();
    } catch (Exception e) {
      System.err.println("Could not start kosit-verification-mcp-service: " + summarize(e));
      System.exit(1);
    }
  }

  private static void configureLoggingDefaults() {
    if (System.getProperty(SIMPLE_LOG_LEVEL_PROPERTY) == null) {
      System.setProperty(SIMPLE_LOG_LEVEL_PROPERTY, "warn");
    }
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

package local.xrechnung.kositviamcpverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

final class CliArguments {

  static final long DEFAULT_TIMEOUT_MILLIS = 120000L;

  private final Path serviceJar;
  private final String xmlPath;
  private final String xmlContent;
  private final String inputName;
  private final boolean persistArtifacts;
  private final long timeoutMillis;
  private final Path workingDirectory;
  private final boolean printRaw;

  private CliArguments(
      Path serviceJar,
      String xmlPath,
      String xmlContent,
      String inputName,
      boolean persistArtifacts,
      long timeoutMillis,
      Path workingDirectory,
      boolean printRaw) {
    this.serviceJar = serviceJar;
    this.xmlPath = xmlPath;
    this.xmlContent = xmlContent;
    this.inputName = inputName;
    this.persistArtifacts = persistArtifacts;
    this.timeoutMillis = timeoutMillis;
    this.workingDirectory = workingDirectory;
    this.printRaw = printRaw;
  }

  static CliArguments parse(String[] args) throws CliUsageException {
    Map<String, String> values = parsePairs(args);
    Path serviceJar = requiredPath(values, "--service-jar");
    if (!Files.isRegularFile(serviceJar)) {
      throw new CliUsageException("Service JAR not found: " + serviceJar);
    }

    String xmlPath = trimToNull(values.get("--xml"));
    String xmlContent = trimToNull(values.get("--xml-content"));
    if ((xmlPath == null) == (xmlContent == null)) {
      throw new CliUsageException("Exactly one of --xml or --xml-content must be provided.");
    }

    long timeoutMillis = parsePositiveLong(
        values.getOrDefault("--timeout-ms", String.valueOf(DEFAULT_TIMEOUT_MILLIS)),
        "--timeout-ms");
    Path cwd = values.containsKey("--cwd")
        ? Paths.get(values.get("--cwd")).toAbsolutePath().normalize()
        : RepositoryLocator.findProjectRootOrCurrent();
    if (!Files.isDirectory(cwd)) {
      throw new CliUsageException("Working directory not found: " + cwd);
    }

    return new CliArguments(
        serviceJar.toAbsolutePath().normalize(),
        xmlPath,
        xmlContent,
        trimToNull(values.get("--input-name")),
        parseBoolean(values.getOrDefault("--persist-artifacts", "false"), "--persist-artifacts"),
        timeoutMillis,
        cwd,
        parseBoolean(values.getOrDefault("--print-raw", "false"), "--print-raw"));
  }

  VerificationRequest toVerificationRequest() {
    if (xmlPath != null) {
      return VerificationRequest.forXmlPath(xmlPath, persistArtifacts);
    }
    return VerificationRequest.forXmlContent(xmlContent, inputName, persistArtifacts);
  }

  McpServiceProcess toServiceProcess() {
    return McpServiceProcess.forServiceJar(serviceJar, workingDirectory);
  }

  McpTimeouts toTimeouts() {
    return McpTimeouts.singleValue(timeoutMillis);
  }

  boolean isPrintRaw() {
    return printRaw;
  }

  static String usage() {
    return ""
        + "Usage: java -jar prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar \\\n"
        + "  --service-jar <path> \\\n"
        + "  --xml <path> | --xml-content <xml> \\\n"
        + "  [--input-name <name>] \\\n"
        + "  [--persist-artifacts true|false] \\\n"
        + "  [--timeout-ms <millis>] \\\n"
        + "  [--cwd <path>] \\\n"
        + "  [--print-raw true|false]";
  }

  private static Map<String, String> parsePairs(String[] args) throws CliUsageException {
    Map<String, String> values = new LinkedHashMap<String, String>();
    int index = 0;
    while (index < args.length) {
      String key = args[index];
      if ("--help".equals(key)) {
        throw new CliUsageException(usage());
      }
      if (!key.startsWith("--")) {
        throw new CliUsageException("Unexpected argument: " + key);
      }
      if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
        throw new CliUsageException("Missing value for " + key);
      }
      values.put(key, args[index + 1]);
      index += 2;
    }
    return values;
  }

  private static Path requiredPath(Map<String, String> values, String key) throws CliUsageException {
    String value = trimToNull(values.get(key));
    if (value == null) {
      throw new CliUsageException("Missing required argument " + key);
    }
    return Paths.get(value).toAbsolutePath().normalize();
  }

  private static long parsePositiveLong(String value, String key) throws CliUsageException {
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        throw new NumberFormatException("not positive");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new CliUsageException(key + " must be a positive number of milliseconds.");
    }
  }

  private static boolean parseBoolean(String value, String key) throws CliUsageException {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new CliUsageException(key + " must be true or false.");
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}

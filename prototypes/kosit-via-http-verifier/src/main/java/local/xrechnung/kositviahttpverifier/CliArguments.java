package local.xrechnung.kositviahttpverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

final class CliArguments {

  static final long DEFAULT_TIMEOUT_MILLIS = 120000L;
  static final long DEFAULT_VALIDATION_TIMEOUT_MILLIS = 300000L;

  private final String xmlPath;
  private final String xmlContent;
  private final String inputName;
  private final boolean persistArtifacts;
  private final long timeoutMillis;
  private final long validationTimeoutMillis;
  private final Path workingDirectory;
  private final boolean printRaw;

  private CliArguments(
      String xmlPath,
      String xmlContent,
      String inputName,
      boolean persistArtifacts,
      long timeoutMillis,
      long validationTimeoutMillis,
      Path workingDirectory,
      boolean printRaw) {
    this.xmlPath = xmlPath;
    this.xmlContent = xmlContent;
    this.inputName = inputName;
    this.persistArtifacts = persistArtifacts;
    this.timeoutMillis = timeoutMillis;
    this.validationTimeoutMillis = validationTimeoutMillis;
    this.workingDirectory = workingDirectory;
    this.printRaw = printRaw;
  }

  static CliArguments parse(String[] args) throws CliUsageException {
    Map<String, String> values = parsePairs(args);
    String xmlPath = trimToNull(values.get("--xml"));
    String xmlContent = trimToNull(values.get("--xml-content"));
    if ((xmlPath == null) == (xmlContent == null)) {
      throw new CliUsageException("Exactly one of --xml or --xml-content must be provided.");
    }

    long timeoutMillis = parsePositiveLong(
        values.getOrDefault("--timeout-ms", String.valueOf(DEFAULT_TIMEOUT_MILLIS)),
        "--timeout-ms");
    long validationTimeoutMillis = parsePositiveLong(
        values.getOrDefault(
            "--validation-timeout-ms",
            String.valueOf(DEFAULT_VALIDATION_TIMEOUT_MILLIS)),
        "--validation-timeout-ms");
    Path cwd = values.containsKey("--cwd")
        ? Paths.get(values.get("--cwd")).toAbsolutePath().normalize()
        : RepositoryLocator.findProjectRootOrCurrent();
    if (!Files.isDirectory(cwd)) {
      throw new CliUsageException("Working directory not found: " + cwd);
    }

    return new CliArguments(
        xmlPath,
        xmlContent,
        trimToNull(values.get("--input-name")),
        parseBoolean(values.getOrDefault("--persist-artifacts", "false"), "--persist-artifacts"),
        timeoutMillis,
        validationTimeoutMillis,
        cwd,
        parseBoolean(values.getOrDefault("--print-raw", "false"), "--print-raw"));
  }

  VerificationRequest toVerificationRequest() {
    if (xmlPath != null) {
      return VerificationRequest.forXmlPath(xmlPath, persistArtifacts);
    }
    return VerificationRequest.forXmlContent(xmlContent, inputName, persistArtifacts);
  }

  HttpWorkerProcess toWorkerProcess() throws CliUsageException {
    return HttpWorkerProcess.forCurrentJar(workingDirectory);
  }

  HttpTimeouts toTimeouts() {
    return HttpTimeouts.of(timeoutMillis, validationTimeoutMillis);
  }

  boolean isPrintRaw() {
    return printRaw;
  }

  static String usage() {
    return ""
        + "Usage: java -jar prototypes/kosit-via-http-verifier/target/kosit-via-http-verifier.jar \\\n"
        + "  --xml <path> | --xml-content <xml> \\\n"
        + "  [--input-name <name>] \\\n"
        + "  [--persist-artifacts true|false] \\\n"
        + "  [--timeout-ms <millis>] \\\n"
        + "  [--validation-timeout-ms <millis>] \\\n"
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
      if (!isKnownArgument(key)) {
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

  private static boolean isKnownArgument(String value) {
    return "--xml".equals(value)
        || "--xml-content".equals(value)
        || "--input-name".equals(value)
        || "--persist-artifacts".equals(value)
        || "--timeout-ms".equals(value)
        || "--validation-timeout-ms".equals(value)
        || "--cwd".equals(value)
        || "--print-raw".equals(value);
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

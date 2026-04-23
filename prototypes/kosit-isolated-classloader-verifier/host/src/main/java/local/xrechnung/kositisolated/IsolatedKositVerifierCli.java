package local.xrechnung.kositisolated;

import local.xrechnung.kositisolated.bridge.VerifierBridge;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IsolatedKositVerifierCli {

  private static final int EXIT_ACCEPTED = 0;
  private static final int EXIT_REJECTED = 1;
  private static final int EXIT_ERROR = 2;
  private static final int EXIT_USAGE = 64;
  private static final String BRIDGE_IMPL =
      "local.xrechnung.kositisolated.impl.KositVerifierBridgeImpl";
  private static final String CONFIG_ZIP_PATTERN =
      "xrechnung-3.0.2-validator-configuration-*.zip";
  private static final String SIMPLE_LOG_LEVEL_PROPERTY =
      "org.slf4j.simpleLogger.defaultLogLevel";
  private static final String SIMPLE_LOG_FILE_PROPERTY =
      "org.slf4j.simpleLogger.logFile";

  private IsolatedKositVerifierCli() {
  }

  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);
    System.exit(exitCode);
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    configureLoggingDefaults();

    CliOptions options;
    try {
      options = CliOptions.parse(args);
    } catch (IllegalArgumentException e) {
      err.println("Error: " + e.getMessage());
      err.println();
      err.print(usage());
      return EXIT_USAGE;
    }

    if (options.help) {
      out.print(usage());
      return EXIT_ACCEPTED;
    }

    try {
      Map<String, Object> result = execute(options);
      String json = JsonSupport.toJson(result);
      writeJsonIfRequested(options.jsonOut, json);
      String status = String.valueOf(result.get("status"));
      out.println(status);
      out.println(json);
      return exitCode(status);
    } catch (Throwable e) {
      Map<String, Object> result = errorResult(options, e);
      String json = JsonSupport.toJson(result);
      try {
        writeJsonIfRequested(options.jsonOut, json);
      } catch (IOException writeError) {
        err.println("Could not write JSON result: " + summarize(writeError));
      }
      err.println("Validation failed: " + summarize(e));
      out.println("ERROR");
      out.println(json);
      return EXIT_ERROR;
    }
  }

  private static void configureLoggingDefaults() {
    if (System.getProperty(SIMPLE_LOG_LEVEL_PROPERTY) == null) {
      System.setProperty(SIMPLE_LOG_LEVEL_PROPERTY, "warn");
    }
    if (System.getProperty(SIMPLE_LOG_FILE_PROPERTY) == null) {
      System.setProperty(SIMPLE_LOG_FILE_PROPERTY, "System.err");
    }
  }

  private static Map<String, Object> execute(CliOptions cliOptions) throws Exception {
    Path moduleRoot = findModuleRoot();
    Path repoRoot = moduleRoot.getParent().getParent();
    Path runtimeLibDir = cliOptions.runtimeLibDir != null
        ? cliOptions.runtimeLibDir.toAbsolutePath().normalize()
        : moduleRoot.resolve("target/kosit-runtime/lib").toAbsolutePath().normalize();

    URL[] runtimeUrls = runtimeUrls(runtimeLibDir);
    ClassLoader hostClassLoader = IsolatedKositVerifierCli.class.getClassLoader();
    try (ChildFirstUrlClassLoader isolatedClassLoader =
             new ChildFirstUrlClassLoader(runtimeUrls, hostClassLoader)) {
      Thread thread = Thread.currentThread();
      ClassLoader previousContextClassLoader = thread.getContextClassLoader();
      thread.setContextClassLoader(isolatedClassLoader);
      try {
        Class<?> implementationClass =
            Class.forName(BRIDGE_IMPL, true, isolatedClassLoader);
        Constructor<?> constructor = implementationClass.getDeclaredConstructor();
        VerifierBridge bridge = (VerifierBridge) constructor.newInstance();
        try {
          Map<String, Object> result = cliOptions.diagnosticsOnly
              ? bridge.diagnostics(diagnosticsRequestMap(runtimeLibDir))
              : bridge.verify(validationRequestMap(cliOptions, repoRoot, moduleRoot));
          enrichHostDiagnostics(result, hostClassLoader, isolatedClassLoader, runtimeLibDir);
          return result;
        } finally {
          bridge.close();
        }
      } finally {
        thread.setContextClassLoader(previousContextClassLoader);
      }
    }
  }

  private static Map<String, String> validationRequestMap(
      CliOptions cliOptions,
      Path repoRoot,
      Path moduleRoot) throws IOException {
    Path xmlPath = requireRegularFile(cliOptions.xmlPath.toAbsolutePath().normalize(), "--xml");
    Path configPath = cliOptions.configPath != null
        ? requireRegularFile(cliOptions.configPath.toAbsolutePath().normalize(), "--config")
        : findConfigZip(repoRoot);
    Path workDir = cliOptions.workDir != null
        ? cliOptions.workDir.toAbsolutePath().normalize()
        : moduleRoot.resolve("target/validator-work").toAbsolutePath().normalize();
    Path reportOut = cliOptions.reportOut != null
        ? cliOptions.reportOut.toAbsolutePath().normalize()
        : moduleRoot.resolve("target/reports/" + stripExtension(xmlPath.getFileName().toString())
            + "-report.xml").toAbsolutePath().normalize();
    return requestMap(xmlPath, configPath, workDir, reportOut, cliOptions.diagnostics);
  }

  private static Map<String, String> requestMap(
      Path xmlPath,
      Path configPath,
      Path workDir,
      Path reportOut,
      boolean diagnostics) {
    Map<String, String> request = new LinkedHashMap<String, String>();
    request.put("xmlPath", xmlPath.toString());
    request.put("configPath", configPath.toString());
    request.put("workDir", workDir.toString());
    request.put("reportOut", reportOut.toString());
    request.put("diagnostics", Boolean.toString(diagnostics));
    return request;
  }

  private static Map<String, String> diagnosticsRequestMap(Path runtimeLibDir) {
    Map<String, String> request = new LinkedHashMap<String, String>();
    request.put("runtimeLibDir", runtimeLibDir.toString());
    request.put("diagnostics", Boolean.TRUE.toString());
    return request;
  }

  @SuppressWarnings("unchecked")
  private static void enrichHostDiagnostics(
      Map<String, Object> result,
      ClassLoader hostClassLoader,
      ClassLoader isolatedClassLoader,
      Path runtimeLibDir) {
    Object rawDiagnostics = result.get("diagnostics");
    if (!(rawDiagnostics instanceof Map<?, ?>)) {
      return;
    }
    Map<String, Object> diagnostics = (Map<String, Object>) rawDiagnostics;
    diagnostics.put("hostClassLoader", describeClassLoader(hostClassLoader));
    diagnostics.put("isolatedClassLoader", describeClassLoader(isolatedClassLoader));
    diagnostics.put("runtimeLibDir", runtimeLibDir.toString());
  }

  private static URL[] runtimeUrls(Path runtimeLibDir) throws IOException {
    if (!Files.isDirectory(runtimeLibDir)) {
      throw new IOException("Missing runtime library directory " + runtimeLibDir
          + ". Run mvn -f prototypes/kosit-isolated-classloader-verifier/pom.xml package first.");
    }

    List<Path> jars = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(runtimeLibDir, "*.jar")) {
      for (Path jar : stream) {
        jars.add(jar.toAbsolutePath().normalize());
      }
    }
    Collections.sort(jars, new Comparator<Path>() {
      @Override
      public int compare(Path left, Path right) {
        return jarSortKey(left).compareTo(jarSortKey(right));
      }
    });
    if (jars.isEmpty()) {
      throw new IOException("No runtime jars found under " + runtimeLibDir);
    }

    URL[] urls = new URL[jars.size()];
    for (int index = 0; index < jars.size(); index++) {
      urls[index] = jars.get(index).toUri().toURL();
    }
    return urls;
  }

  private static String jarSortKey(Path jar) {
    String name = jar.getFileName().toString();
    return name.startsWith("kosit-isolated-adapter") ? "0-" + name : "1-" + name;
  }

  private static Path findConfigZip(Path repoRoot) throws IOException {
    Path bundleDocs = repoRoot.resolve("bundle-docs");
    List<Path> matches = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(bundleDocs, CONFIG_ZIP_PATTERN)) {
      for (Path entry : stream) {
        matches.add(entry.toAbsolutePath().normalize());
      }
    }
    Collections.sort(matches);
    if (matches.isEmpty()) {
      throw new IOException("No " + CONFIG_ZIP_PATTERN + " found under " + bundleDocs);
    }
    return matches.get(matches.size() - 1);
  }

  private static Path findModuleRoot() throws IOException {
    CodeSource codeSource =
        IsolatedKositVerifierCli.class.getProtectionDomain().getCodeSource();
    if (codeSource == null || codeSource.getLocation() == null) {
      return Paths.get("").toAbsolutePath().normalize()
          .resolve("prototypes/kosit-isolated-classloader-verifier").normalize();
    }
    try {
      Path location = Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
      if (Files.isRegularFile(location) && location.getParent() != null
          && "target".equals(location.getParent().getFileName().toString())) {
        return location.getParent().getParent();
      }
      Path candidate = location;
      while (candidate != null) {
        if (Files.isRegularFile(candidate.resolve("pom.xml"))
            && candidate.getFileName() != null
            && "kosit-isolated-classloader-verifier".equals(candidate.getFileName().toString())) {
          return candidate;
        }
        candidate = candidate.getParent();
      }
    } catch (URISyntaxException e) {
      throw new IOException("Could not resolve CLI module root", e);
    }
    return Paths.get("").toAbsolutePath().normalize()
        .resolve("prototypes/kosit-isolated-classloader-verifier").normalize();
  }

  private static Path requireRegularFile(Path path, String optionName) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException(optionName + " does not point to a readable file: " + path);
    }
    return path;
  }

  private static void writeJsonIfRequested(Path jsonOut, String json) throws IOException {
    if (jsonOut == null) {
      return;
    }
    Path parent = jsonOut.toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(jsonOut, json + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  private static int exitCode(String status) {
    if ("ACCEPTED".equals(status)) {
      return EXIT_ACCEPTED;
    }
    if ("REJECTED".equals(status)) {
      return EXIT_REJECTED;
    }
    if ("DIAGNOSTICS".equals(status)) {
      return EXIT_ACCEPTED;
    }
    return EXIT_ERROR;
  }

  private static Map<String, Object> errorResult(CliOptions options, Throwable error) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("status", "ERROR");
    result.put("xmlPath", options.xmlPath != null ? options.xmlPath.toString() : null);
    result.put("configPath", options.configPath != null ? options.configPath.toString() : null);
    result.put("reportPath", options.reportOut != null ? options.reportOut.toString() : null);
    List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    Map<String, Object> message = new LinkedHashMap<String, Object>();
    message.put("channel", "host");
    message.put("severity", "error");
    message.put("message", summarize(error));
    message.put("exception", error.getClass().getName());
    messages.add(message);
    result.put("messages", messages);
    return result;
  }

  private static String describeClassLoader(ClassLoader classLoader) {
    if (classLoader == null) {
      return "bootstrap";
    }
    return classLoader.getClass().getName() + "@"
        + Integer.toHexString(System.identityHashCode(classLoader));
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  private static String summarize(Throwable error) {
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

  private static String usage() {
    return "Usage: java -jar target/kosit-isolated-classloader-verifier.jar "
        + "(--xml <path> | --diagnostics-only) [options]\n"
        + "\n"
        + "Options:\n"
        + "  --xml <path>                XML file to validate\n"
        + "  --diagnostics-only          Load isolated runtime and print diagnostics only\n"
        + "  --config <zip>              XRechnung validator configuration ZIP\n"
        + "  --runtime-lib-dir <path>    Isolated runtime jars, default target/kosit-runtime/lib\n"
        + "  --work-dir <path>           Extraction work dir, default target/validator-work\n"
        + "  --report-out <path>         XML report path, default target/reports/<xml>-report.xml\n"
        + "  --json-out <path>           Optional JSON result path\n"
        + "  --diagnostics               Include ClassLoader and code-source diagnostics\n"
        + "  --help                      Show this help\n";
  }

  private static final class CliOptions {
    private boolean help;
    private boolean diagnostics;
    private boolean diagnosticsOnly;
    private Path xmlPath;
    private Path configPath;
    private Path runtimeLibDir;
    private Path workDir;
    private Path reportOut;
    private Path jsonOut;

    private static CliOptions parse(String[] args) {
      CliOptions options = new CliOptions();
      for (int index = 0; index < args.length; index++) {
        String arg = args[index];
        if ("--help".equals(arg) || "-h".equals(arg)) {
          options.help = true;
        } else if ("--diagnostics".equals(arg)) {
          options.diagnostics = true;
        } else if ("--diagnostics-only".equals(arg)) {
          options.diagnosticsOnly = true;
          options.diagnostics = true;
        } else if ("--xml".equals(arg)) {
          options.xmlPath = pathValue(args, ++index, arg);
        } else if ("--config".equals(arg)) {
          options.configPath = pathValue(args, ++index, arg);
        } else if ("--runtime-lib-dir".equals(arg)) {
          options.runtimeLibDir = pathValue(args, ++index, arg);
        } else if ("--work-dir".equals(arg)) {
          options.workDir = pathValue(args, ++index, arg);
        } else if ("--report-out".equals(arg)) {
          options.reportOut = pathValue(args, ++index, arg);
        } else if ("--json-out".equals(arg)) {
          options.jsonOut = pathValue(args, ++index, arg);
        } else {
          throw new IllegalArgumentException("Unknown argument " + arg);
        }
      }
      if (!options.help && !options.diagnosticsOnly && options.xmlPath == null) {
        throw new IllegalArgumentException("Missing required --xml <path>");
      }
      return options;
    }

    private static Path pathValue(String[] args, int index, String optionName) {
      if (index >= args.length || args[index].startsWith("--")) {
        throw new IllegalArgumentException("Missing value for " + optionName);
      }
      return Paths.get(args[index]);
    }
  }
}

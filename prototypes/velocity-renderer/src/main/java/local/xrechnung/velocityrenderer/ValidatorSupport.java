package local.xrechnung.velocityrenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves the Maven-provided KoSIT validator and a committed XRechnung
 * configuration ZIP, then runs validation against rendered XML files.
 */
final class ValidatorSupport {

  private static final String VALIDATOR_JAR_NAME = "validator-1.6.2-standalone.jar";
  private static final String CONFIG_ZIP_PREFIX = "xrechnung-3.0.2-validator-configuration-";
  private static final String CONFIG_ZIP_SUFFIX = ".zip";

  private ValidatorSupport() {
  }

  static ValidationResult validate(
      Path xmlPath,
      Path projectRoot,
      Path cacheDirOverride) throws Exception {
    Path workDir = cacheDirOverride != null
        ? cacheDirOverride.toAbsolutePath()
        : projectRoot.resolve("prototypes/velocity-renderer/target/validator-work").toAbsolutePath();

    ValidatorArtifacts artifacts = prepareArtifacts(projectRoot, workDir);
    return runValidator(xmlPath.toAbsolutePath(), artifacts.validatorJar, artifacts.configDirectory);
  }

  private static Path resolveConfigZip(Path projectRoot) throws IOException {
    Path bundleDocs = projectRoot.resolve("bundle-docs");
    List<Path> matches = new ArrayList<Path>();
    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(bundleDocs, CONFIG_ZIP_PREFIX + "*" + CONFIG_ZIP_SUFFIX)) {
      for (Path entry : stream) {
        matches.add(entry.toAbsolutePath());
      }
    }
    Collections.sort(matches);
    if (matches.isEmpty()) {
      throw new IOException(
          "No validator configuration ZIP found under " + bundleDocs
              + ". Add bundle-docs/" + CONFIG_ZIP_PREFIX + "*" + CONFIG_ZIP_SUFFIX + ".");
    }
    return matches.get(matches.size() - 1);
  }

  private static ValidatorArtifacts prepareArtifacts(Path projectRoot, Path workDir) throws IOException {
    Files.createDirectories(workDir);
    Path validatorJar = resolveValidatorJar(projectRoot);
    Path configZip = resolveConfigZip(projectRoot);
    Path configDir = workDir.resolve(stripZipSuffix(configZip.getFileName().toString()));

    extractZipIfNeeded(
        configZip,
        configDir,
        Paths.get("resources/cii/16b/xsd/CrossIndustryInvoice_100pD16B.xsd"));

    return new ValidatorArtifacts(validatorJar, configDir);
  }

  private static Path resolveValidatorJar(Path projectRoot) throws IOException {
    Path validatorJar =
        projectRoot.resolve("prototypes/velocity-renderer/target/validator-bin")
            .resolve(VALIDATOR_JAR_NAME)
            .toAbsolutePath();
    if (!Files.exists(validatorJar)) {
      throw new IOException(
          "Validator JAR not found at " + validatorJar
              + ". Run 'mvn -f prototypes/velocity-renderer/pom.xml package' first.");
    }
    return validatorJar;
  }

  private static void extractZipIfNeeded(
      Path zipPath,
      Path targetDir,
      Path markerRelativePath) throws IOException {
    if (Files.exists(targetDir.resolve(markerRelativePath))) {
      return;
    }

    Path parentDir = targetDir.getParent();
    if (parentDir == null) {
      throw new IOException("Missing parent directory for " + targetDir);
    }
    Files.createDirectories(parentDir);
    Path tempDir = Files.createTempDirectory(parentDir, targetDir.getFileName().toString() + ".tmp-");

    try (InputStream rawStream = Files.newInputStream(zipPath);
         ZipInputStream nestedZip = new ZipInputStream(rawStream)) {
      ZipEntry entry;
      while ((entry = nestedZip.getNextEntry()) != null) {
        Path outputPath = tempDir.resolve(entry.getName()).normalize();
        if (!outputPath.startsWith(tempDir)) {
          throw new IOException("Blocked suspicious ZIP entry " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(outputPath);
        } else {
          Path parent = outputPath.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          Files.copy(nestedZip, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        nestedZip.closeEntry();
      }
    }

    deleteRecursively(targetDir);
    try {
      Files.move(tempDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException alreadyExists) {
      deleteRecursively(tempDir);
      if (!Files.exists(targetDir.resolve(markerRelativePath))) {
        throw alreadyExists;
      }
    }
  }

  private static ValidationResult runValidator(Path xmlPath, Path validatorJar, Path configDir)
      throws IOException, InterruptedException {
    Path reportDir = xmlPath.getParent();
    if (reportDir == null) {
      reportDir = Paths.get(".").toAbsolutePath();
    }

    String baseName = stripExtension(xmlPath.getFileName().toString());
    Path reportXml = reportDir.resolve(baseName + "-report.xml");
    Path reportHtml = reportDir.resolve(baseName + "-report.html");
    Files.deleteIfExists(reportXml);
    Files.deleteIfExists(reportHtml);

    List<String> command = new ArrayList<String>();
    command.add(resolveJavaExecutable());
    command.add("-jar");
    command.add(validatorJar.toString());
    command.add("-s");
    command.add(configDir.resolve("scenarios.xml").toString());
    command.add("-r");
    command.add(configDir.toString());
    command.add("-h");
    command.add(xmlPath.toString());

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(reportDir.toFile());
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    String output;
    try (InputStream input = process.getInputStream()) {
      output = readAll(input);
    }
    int exitCode = process.waitFor();

    return new ValidationResult(
        exitCode,
        output,
        Files.exists(reportXml) ? reportXml : null,
        Files.exists(reportHtml) ? reportHtml : null);
  }

  private static String resolveJavaExecutable() {
    String executable = System.getProperty("os.name").toLowerCase().contains("win")
        ? "java.exe"
        : "java";
    return Paths.get(System.getProperty("java.home"), "bin", executable).toString();
  }

  private static String readAll(InputStream input) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] bytes = new byte[8192];
    int read;
    while ((read = input.read(bytes)) >= 0) {
      buffer.write(bytes, 0, read);
    }
    return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String stripZipSuffix(String name) {
    if (name.endsWith(".zip")) {
      return name.substring(0, name.length() - 4);
    }
    return name;
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  static final class ValidationResult {
    private final int exitCode;
    private final String output;
    private final Path reportXml;
    private final Path reportHtml;

    ValidationResult(int exitCode, String output, Path reportXml, Path reportHtml) {
      this.exitCode = exitCode;
      this.output = output;
      this.reportXml = reportXml;
      this.reportHtml = reportHtml;
    }

    int getExitCode() {
      return exitCode;
    }

    String getOutput() {
      return output;
    }

    Path getReportXml() {
      return reportXml;
    }

    Path getReportHtml() {
      return reportHtml;
    }

    boolean isSuccessful() {
      return exitCode == 0;
    }
  }

  private static final class ValidatorArtifacts {
    private final Path validatorJar;
    private final Path configDirectory;

    private ValidatorArtifacts(Path validatorJar, Path configDirectory) {
      this.validatorJar = validatorJar;
      this.configDirectory = configDirectory;
    }
  }
}

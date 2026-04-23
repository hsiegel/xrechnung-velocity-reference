package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ValidatorConfigSupport {

  private static final String CONFIG_ZIP_PREFIX = "xrechnung-3.0.2-validator-configuration-";
  private static final String CONFIG_ZIP_SUFFIX = ".zip";
  private static final Path READY_MARKER =
      Path.of("resources/ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd");

  private ValidatorConfigSupport() {
  }

  static PreparedConfiguration prepare(Path projectRoot, Path workDir) throws IOException {
    Files.createDirectories(workDir);

    Path configZip = resolveConfigZip(projectRoot);
    Path configDirectory = workDir.resolve(stripZipSuffix(configZip.getFileName().toString()));
    extractZipIfNeeded(configZip, configDirectory, READY_MARKER);

    Path scenariosFile = configDirectory.resolve("scenarios.xml");
    if (!Files.isRegularFile(scenariosFile)) {
      throw new IOException("Missing scenarios.xml in " + configDirectory);
    }

    return new PreparedConfiguration(
        configZip.toAbsolutePath(),
        configDirectory.toAbsolutePath(),
        scenariosFile.toAbsolutePath());
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
         ZipInputStream zipStream = new ZipInputStream(rawStream)) {
      ZipEntry entry;
      while ((entry = zipStream.getNextEntry()) != null) {
        Path outputPath = tempDir.resolve(entry.getName()).normalize();
        if (!outputPath.startsWith(tempDir)) {
          throw new IOException("Blocked suspicious ZIP entry " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(outputPath);
        } else {
          Path outputParent = outputPath.getParent();
          if (outputParent != null) {
            Files.createDirectories(outputParent);
          }
          Files.copy(zipStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zipStream.closeEntry();
      }
    } catch (IOException e) {
      deleteRecursively(tempDir);
      throw e;
    }

    deleteRecursively(targetDir);
    try {
      Files.move(tempDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      deleteRecursively(tempDir);
      if (!Files.exists(targetDir.resolve(markerRelativePath))) {
        throw e;
      }
    }
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

  private static String stripZipSuffix(String name) {
    if (name.endsWith(".zip")) {
      return name.substring(0, name.length() - 4);
    }
    return name;
  }

  static final class PreparedConfiguration {
    private final Path configZip;
    private final Path configDirectory;
    private final Path scenariosFile;

    PreparedConfiguration(Path configZip, Path configDirectory, Path scenariosFile) {
      this.configZip = configZip;
      this.configDirectory = configDirectory;
      this.scenariosFile = scenariosFile;
    }

    Path getConfigZip() {
      return configZip;
    }

    Path getConfigDirectory() {
      return configDirectory;
    }

    Path getScenariosFile() {
      return scenariosFile;
    }
  }
}

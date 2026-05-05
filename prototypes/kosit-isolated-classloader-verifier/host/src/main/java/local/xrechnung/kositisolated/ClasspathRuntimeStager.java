package local.xrechnung.kositisolated;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

final class ClasspathRuntimeStager {

  private static final String RESOURCE_ROOT = "kosit-isolated";
  private static final String RUNTIME_JARS_MANIFEST = RESOURCE_ROOT + "/runtime-jars.list";
  private static final String RUNTIME_LIB_PREFIX = RESOURCE_ROOT + "/runtime-lib/";
  private static final String CONFIG_RESOURCE =
      RESOURCE_ROOT + "/config/xrechnung-3.0.2-validator-configuration-2026-01-31.zip";

  private static StagedRuntime cachedDefaultRuntime;

  private ClasspathRuntimeStager() {
  }

  static synchronized StagedRuntime stage(ClassLoader classLoader, Path requestedStageRoot)
      throws IOException {
    if (requestedStageRoot == null
        && cachedDefaultRuntime != null
        && Files.isDirectory(cachedDefaultRuntime.runtimeLibDir)) {
      return cachedDefaultRuntime;
    }

    Path stageRoot = requestedStageRoot != null
        ? requestedStageRoot.toAbsolutePath().normalize()
        : Files.createTempDirectory("kosit-isolated-runtime-");
    StagedRuntime stagedRuntime = stageInto(classLoader, stageRoot);
    if (requestedStageRoot == null) {
      cachedDefaultRuntime = stagedRuntime;
      registerCleanup(stageRoot);
    }
    return stagedRuntime;
  }

  private static StagedRuntime stageInto(ClassLoader classLoader, Path stageRoot)
      throws IOException {
    Path runtimeLibDir = stageRoot.resolve("runtime-lib");
    Path configDir = stageRoot.resolve("config");
    Path workDir = stageRoot.resolve("validator-work");
    Path reportDir = stageRoot.resolve("reports");
    Path configZip = configDir.resolve(fileName(CONFIG_RESOURCE));

    deleteRecursively(runtimeLibDir);
    Files.createDirectories(runtimeLibDir);
    Files.createDirectories(configDir);
    Files.createDirectories(workDir);
    Files.createDirectories(reportDir);

    for (String jarName : readRuntimeJarNames(classLoader)) {
      copyResource(classLoader, RUNTIME_LIB_PREFIX + jarName, runtimeLibDir.resolve(jarName));
    }
    copyResource(classLoader, CONFIG_RESOURCE, configZip);

    return new StagedRuntime(stageRoot, runtimeLibDir, configZip, workDir, reportDir);
  }

  private static List<String> readRuntimeJarNames(ClassLoader classLoader) throws IOException {
    List<String> jarNames = new ArrayList<String>();
    try (InputStream input = openResource(classLoader, RUNTIME_JARS_MANIFEST);
         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
         BufferedReader bufferedReader = new BufferedReader(reader)) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        String jarName = line.trim();
        if (jarName.isEmpty() || jarName.startsWith("#")) {
          continue;
        }
        validateJarName(jarName);
        jarNames.add(jarName);
      }
    }
    if (jarNames.isEmpty()) {
      throw new IOException("No runtime jars listed in classpath resource "
          + RUNTIME_JARS_MANIFEST);
    }
    return jarNames;
  }

  private static void validateJarName(String jarName) throws IOException {
    if (!jarName.endsWith(".jar")
        || jarName.contains("/")
        || jarName.contains("\\")
        || jarName.contains("..")) {
      throw new IOException("Invalid runtime jar entry in " + RUNTIME_JARS_MANIFEST
          + ": " + jarName);
    }
  }

  private static void copyResource(ClassLoader classLoader, String resourceName, Path target)
      throws IOException {
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("Missing parent directory for staged resource " + target);
    }
    Files.createDirectories(parent);
    Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
    try (InputStream input = openResource(classLoader, resourceName)) {
      Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
      Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      Files.deleteIfExists(tempFile);
      throw e;
    }
  }

  private static InputStream openResource(ClassLoader classLoader, String resourceName)
      throws IOException {
    InputStream input = classLoader.getResourceAsStream(resourceName);
    if (input == null) {
      throw new IOException("Missing classpath resource " + resourceName);
    }
    return input;
  }

  private static String fileName(String resourceName) {
    int slash = resourceName.lastIndexOf('/');
    return slash >= 0 ? resourceName.substring(slash + 1) : resourceName;
  }

  private static void registerCleanup(final Path stageRoot) {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          deleteRecursively(stageRoot);
        } catch (IOException ignored) {
          // Best-effort cleanup for prototype temp staging.
        }
      }
    }, "kosit-isolated-runtime-cleanup"));
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

  static final class StagedRuntime {
    final Path stageRoot;
    final Path runtimeLibDir;
    final Path configZip;
    final Path workDir;
    final Path reportDir;

    private StagedRuntime(
        Path stageRoot,
        Path runtimeLibDir,
        Path configZip,
        Path workDir,
        Path reportDir) {
      this.stageRoot = stageRoot;
      this.runtimeLibDir = runtimeLibDir;
      this.configZip = configZip;
      this.workDir = workDir;
      this.reportDir = reportDir;
    }
  }
}

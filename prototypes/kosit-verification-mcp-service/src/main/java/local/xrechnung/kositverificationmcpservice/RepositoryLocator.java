package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class RepositoryLocator {

  private static final String MODULE_DIR = "prototypes/kosit-verification-mcp-service";

  private RepositoryLocator() {
  }

  static Path findProjectRoot() throws IOException {
    Path fromWorkingDirectory = findFrom(Paths.get("").toAbsolutePath().normalize());
    if (fromWorkingDirectory != null) {
      return fromWorkingDirectory;
    }

    try {
      Path codeSource = Paths.get(
          RepositoryLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (!Files.isDirectory(codeSource)) {
        codeSource = codeSource.getParent();
      }
      Path fromCodeSource = findFrom(codeSource.toAbsolutePath().normalize());
      if (fromCodeSource != null) {
        return fromCodeSource;
      }
    } catch (URISyntaxException e) {
      throw new IOException("Could not inspect the server location.", e);
    }

    throw new IOException(
        "Could not locate the repository root. Expected bundle-docs/ and "
            + MODULE_DIR + "/ above the current directory or the packaged JAR.");
  }

  private static Path findFrom(Path start) {
    Path current = start;
    while (current != null) {
      if (isProjectRoot(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private static boolean isProjectRoot(Path candidate) {
    return Files.isDirectory(candidate.resolve("bundle-docs"))
        && Files.isDirectory(candidate.resolve(MODULE_DIR));
  }
}

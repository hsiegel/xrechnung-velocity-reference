package local.xrechnung.saxonoutputverifier;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class RepositoryLocator {

  private RepositoryLocator() {
  }

  static Path findProjectRoot() {
    Path fromWorkingDirectory = search(Paths.get("").toAbsolutePath().normalize());
    if (fromWorkingDirectory != null) {
      return fromWorkingDirectory;
    }

    try {
      Path codeSource = Paths.get(
          RepositoryLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      Path fromCodeSource = search(codeSource.toAbsolutePath().normalize());
      if (fromCodeSource != null) {
        return fromCodeSource;
      }
    } catch (URISyntaxException ignored) {
      // fall through to the final error
    }

    throw new IllegalArgumentException(
        "Could not locate the repository root. Expected bundle-docs/ and "
            + "prototypes/saxon-output-verifier-spike/ above the current directory or the packaged JAR.");
  }

  private static Path search(Path start) {
    Path current = Files.isDirectory(start) ? start : start.getParent();
    while (current != null) {
      if (Files.isDirectory(current.resolve("bundle-docs"))
          && Files.isDirectory(current.resolve("prototypes/saxon-output-verifier-spike"))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }
}

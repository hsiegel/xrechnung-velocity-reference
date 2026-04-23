package local.xrechnung.kositviamcpverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class RepositoryLocator {

  private static final String MODULE_DIR = "prototypes/kosit-via-mcp-verifier";

  private RepositoryLocator() {
  }

  static Path findProjectRootOrCurrent() {
    Path found = findFrom(Paths.get("").toAbsolutePath().normalize());
    return found != null ? found : Paths.get("").toAbsolutePath().normalize();
  }

  private static Path findFrom(Path start) {
    Path current = start;
    while (current != null) {
      if (Files.isDirectory(current.resolve("bundle-docs"))
          && Files.isDirectory(current.resolve(MODULE_DIR))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }
}

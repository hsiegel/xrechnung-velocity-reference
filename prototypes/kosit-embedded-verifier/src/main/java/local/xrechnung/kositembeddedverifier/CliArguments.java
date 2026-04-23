package local.xrechnung.kositembeddedverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class CliArguments {

  private final boolean help;
  private final Path xmlPath;
  private final Path reportDir;
  private final Path workDir;

  private CliArguments(boolean help, Path xmlPath, Path reportDir, Path workDir) {
    this.help = help;
    this.xmlPath = xmlPath;
    this.reportDir = reportDir;
    this.workDir = workDir;
  }

  static CliArguments parse(String[] args) {
    boolean help = false;
    Path xmlPath = null;
    Path reportDir = null;
    Path workDir = null;

    for (int index = 0; index < args.length; index++) {
      String argument = args[index];
      if ("--help".equals(argument)) {
        help = true;
      } else if ("--xml".equals(argument)) {
        if (xmlPath != null) {
          throw new IllegalArgumentException("Argument --xml was provided more than once.");
        }
        xmlPath = readPathValue(argument, args, ++index);
      } else if ("--report-dir".equals(argument)) {
        if (reportDir != null) {
          throw new IllegalArgumentException("Argument --report-dir was provided more than once.");
        }
        reportDir = readPathValue(argument, args, ++index);
      } else if ("--work-dir".equals(argument)) {
        if (workDir != null) {
          throw new IllegalArgumentException("Argument --work-dir was provided more than once.");
        }
        workDir = readPathValue(argument, args, ++index);
      } else {
        throw new IllegalArgumentException("Unknown argument: " + argument);
      }
    }

    if (!help && xmlPath == null) {
      throw new IllegalArgumentException("Missing required argument --xml <path>.");
    }

    return new CliArguments(help, xmlPath, reportDir, workDir);
  }

  static String usage() {
    return ""
        + "Usage: java -jar kosit-embedded-verifier.jar --xml <path> "
        + "[--report-dir <dir>] [--work-dir <dir>] [--help]\n"
        + "\n"
        + "Validates an existing XML file with the embedded KoSIT validator API.\n";
  }

  boolean isHelp() {
    return help;
  }

  Path requireXmlPath() {
    if (xmlPath == null) {
      throw new IllegalStateException("No XML path configured.");
    }
    Path resolved = xmlPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(resolved)) {
      throw new IllegalArgumentException("XML file not found: " + resolved);
    }
    return resolved;
  }

  Path resolveReportDir(Path xmlFile) {
    if (reportDir != null) {
      return reportDir.toAbsolutePath().normalize();
    }
    Path parent = xmlFile.getParent();
    if (parent != null) {
      return parent.toAbsolutePath().normalize();
    }
    return Paths.get("").toAbsolutePath().normalize();
  }

  Path resolveWorkDir(Path projectRoot) {
    if (workDir != null) {
      return workDir.toAbsolutePath().normalize();
    }
    return projectRoot.resolve("prototypes/kosit-embedded-verifier/target/validator-work")
        .toAbsolutePath()
        .normalize();
  }

  private static Path readPathValue(String option, String[] args, int index) {
    if (index >= args.length) {
      throw new IllegalArgumentException("Missing value for " + option + ".");
    }
    return Paths.get(args[index]);
  }
}

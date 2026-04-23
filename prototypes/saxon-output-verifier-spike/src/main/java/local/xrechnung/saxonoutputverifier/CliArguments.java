package local.xrechnung.saxonoutputverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class CliArguments {

  private static final String DEFAULT_PROFILE_ID = ProfileRegistry.DEFAULT_PROFILE_ID;

  private final boolean help;
  private final Path xmlPath;
  private final Path workDir;
  private final String profileId;
  private final Path resultJsonPath;

  private CliArguments(
      boolean help,
      Path xmlPath,
      Path workDir,
      String profileId,
      Path resultJsonPath) {
    this.help = help;
    this.xmlPath = xmlPath;
    this.workDir = workDir;
    this.profileId = profileId;
    this.resultJsonPath = resultJsonPath;
  }

  static CliArguments parse(String[] args) {
    boolean help = false;
    Path xmlPath = null;
    Path workDir = null;
    String profileId = DEFAULT_PROFILE_ID;
    Path resultJsonPath = null;

    for (int index = 0; index < args.length; index++) {
      String arg = args[index];
      if ("--help".equals(arg) || "-h".equals(arg)) {
        help = true;
      } else if ("--xml".equals(arg)) {
        index = requireValueIndex(args, index, arg);
        xmlPath = Paths.get(args[index]);
      } else if ("--work-dir".equals(arg)) {
        index = requireValueIndex(args, index, arg);
        workDir = Paths.get(args[index]);
      } else if ("--profile".equals(arg)) {
        index = requireValueIndex(args, index, arg);
        profileId = args[index];
      } else if ("--result-json".equals(arg)) {
        index = requireValueIndex(args, index, arg);
        resultJsonPath = Paths.get(args[index]);
      } else {
        throw new IllegalArgumentException("Unknown argument: " + arg);
      }
    }

    return new CliArguments(help, xmlPath, workDir, profileId, resultJsonPath);
  }

  boolean isHelp() {
    return help;
  }

  Path requireXmlPath() {
    if (xmlPath == null) {
      throw new IllegalArgumentException("Missing required argument --xml");
    }
    Path absolutePath = xmlPath.toAbsolutePath().normalize();
    if (!Files.exists(absolutePath)) {
      throw new IllegalArgumentException("XML file not found: " + absolutePath);
    }
    if (!Files.isRegularFile(absolutePath)) {
      throw new IllegalArgumentException("XML path is not a file: " + absolutePath);
    }
    return absolutePath;
  }

  Path resolveWorkDir(Path projectRoot) {
    if (workDir != null) {
      return workDir.toAbsolutePath().normalize();
    }
    return projectRoot.resolve("prototypes/saxon-output-verifier-spike/target/validator-work")
        .toAbsolutePath()
        .normalize();
  }

  String getProfileId() {
    return profileId;
  }

  Path resolveResultJsonPath(Path resolvedWorkDir, Path resolvedXmlPath) {
    if (resultJsonPath != null) {
      return resultJsonPath.toAbsolutePath().normalize();
    }
    String baseName = stripExtension(resolvedXmlPath.getFileName().toString());
    return resolvedWorkDir.resolve("results").resolve(baseName + "-result.json")
        .toAbsolutePath()
        .normalize();
  }

  static String usage() {
    return ""
        + "Usage: java -jar saxon-output-verifier-spike.jar --xml FILE"
        + " [--profile ID] [--work-dir DIR] [--result-json FILE]\n"
        + "\n"
        + "This verifier checks a preselected outgoing invoice profile.\n"
        + "Available profiles: " + ProfileRegistry.describeAvailableProfiles() + "\n";
  }

  private static int requireValueIndex(String[] args, int currentIndex, String option) {
    int valueIndex = currentIndex + 1;
    if (valueIndex >= args.length) {
      throw new IllegalArgumentException("Missing value after " + option);
    }
    return valueIndex;
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }
}

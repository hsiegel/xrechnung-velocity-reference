package local.xrechnung.velocityrenderer;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * Minimal local renderer for the semantic-model-driven XRechnung Velocity templates.
 */
public final class VelocityRendererCli {

  private VelocityRendererCli() {
  }

  public static void main(String[] args) {
    try {
      run(args);
    } catch (IllegalArgumentException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (Exception ex) {
      ex.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static void run(String[] args) throws Exception {
    CliOptions options = CliOptions.parse(args);

    if (options.checkModel) {
      runModelCheck(options);
      return;
    }

    if (!options.skipModelCheck) {
      ensureModelConforms(options);
    }

    VelocityEngine engine = createEngine(options.projectRoot);
    Template template = engine.getTemplate(options.templatePath, StandardCharsets.UTF_8.name());

    VelocityContext context = new VelocityContext();
    context.put("xr", InvoiceModelLoader.load(options.modelPath));
    context.put("xml", XmlHelper.INSTANCE);

    StringWriter writer = new StringWriter();
    template.merge(context, writer);
    String xml = writer.toString();

    Path renderedPath = options.outputPath;
    if (renderedPath == null && options.validate) {
      renderedPath = defaultValidationOutputPath(options.projectRoot, options.templatePath);
    }

    if (renderedPath != null) {
      writeFile(renderedPath, xml);
      PrintStream statusStream = options.outputPath == null ? System.err : System.out;
      statusStream.println("Rendered " + options.templatePath + " -> " + renderedPath.toAbsolutePath());
    }

    if (options.validate) {
      ValidatorSupport.ValidationResult validation =
          ValidatorSupport.validate(
              renderedPath,
              options.projectRoot,
              options.validatorWorkDir);
      PrintStream statusStream = options.outputPath == null ? System.err : System.out;
      statusStream.print(validation.getOutput());
      if (validation.getReportXml() != null) {
        statusStream.println("Validator XML report: " + validation.getReportXml().toAbsolutePath());
      }
      if (validation.getReportHtml() != null) {
        statusStream.println("Validator HTML report: " + validation.getReportHtml().toAbsolutePath());
      }
      if (!validation.isSuccessful()) {
        throw new IOException("Validation failed for " + renderedPath.toAbsolutePath());
      }
    }

    if (options.outputPath == null) {
      System.out.print(xml);
    }
  }

  private static void runModelCheck(CliOptions options) throws IOException {
    ModelSchemaValidator.ValidationResult validationResult =
        ModelSchemaValidator.validate(options.projectRoot, options.modelPath);
    if (validationResult.isSuccessful()) {
      System.out.println(
          "Model conforms to " + validationResult.getSchemaPath().toAbsolutePath());
      return;
    }

    for (String message : validationResult.getMessages()) {
      System.err.println(message);
    }
    throw new IOException("Model validation failed for " + options.modelPath.toAbsolutePath());
  }

  private static void ensureModelConforms(CliOptions options) throws IOException {
    ModelSchemaValidator.ValidationResult validationResult =
        ModelSchemaValidator.validate(options.projectRoot, options.modelPath);
    if (validationResult.isSuccessful()) {
      return;
    }

    for (String message : validationResult.getMessages()) {
      System.err.println(message);
    }
    throw new IOException(
        "Model validation failed for " + options.modelPath.toAbsolutePath()
            + ". Fix the model or rerun with --no-model-check.");
  }

  private static VelocityEngine createEngine(Path projectRoot) throws Exception {
    Properties props = new Properties();
    props.setProperty("resource.loader", "file");
    props.setProperty(
        "file.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
    props.setProperty("file.resource.loader.path", projectRoot.toAbsolutePath().toString());
    props.setProperty("input.encoding", StandardCharsets.UTF_8.name());
    props.setProperty("output.encoding", StandardCharsets.UTF_8.name());

    // Our templates define inline macros and rely on null-tolerant omission.
    props.setProperty("velocimacro.permissions.allow.inline", "true");
    props.setProperty("velocimacro.permissions.allow.inline.local.scope", "true");
    props.setProperty("velocimacro.context.localscope", "true");
    props.setProperty("runtime.references.strict", "false");

    VelocityEngine engine = new VelocityEngine();
    engine.init(props);
    return engine;
  }

  private static void writeFile(Path outputPath, String xml) throws IOException {
    Path absolute = outputPath.toAbsolutePath();
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(absolute, xml.getBytes(StandardCharsets.UTF_8));
  }

  private static Path defaultValidationOutputPath(Path projectRoot, String templatePath) throws IOException {
    Path tempDir = projectRoot.resolve("prototypes/velocity-renderer/target/rendered");
    Files.createDirectories(tempDir);
    Path templateName = Paths.get(templatePath).getFileName();
    String fileName = templateName == null ? "rendered-invoice.xml" : templateName.toString();
    if (fileName.endsWith(".vm")) {
      fileName = fileName.substring(0, fileName.length() - 3) + ".xml";
    }
    return tempDir.resolve(fileName);
  }

  private static final class CliOptions {
    private static final String DEFAULT_TEMPLATE = "templates/ubl-invoice-full.vm";

    private final Path projectRoot;
    private final String templatePath;
    private final Path modelPath;
    private final Path outputPath;
    private final boolean validate;
    private final boolean checkModel;
    private final boolean skipModelCheck;
    private final Path validatorWorkDir;

    private CliOptions(
        Path projectRoot,
        String templatePath,
        Path modelPath,
        Path outputPath,
        boolean validate,
        boolean checkModel,
        boolean skipModelCheck,
        Path validatorWorkDir) {
      this.projectRoot = projectRoot;
      this.templatePath = templatePath;
      this.modelPath = modelPath;
      this.outputPath = outputPath;
      this.validate = validate;
      this.checkModel = checkModel;
      this.skipModelCheck = skipModelCheck;
      this.validatorWorkDir = validatorWorkDir;
    }

    private static CliOptions parse(String[] args) {
      Path projectRoot = Paths.get("").toAbsolutePath();
      String templatePath = DEFAULT_TEMPLATE;
      Path modelPath = null;
      Path outputPath = null;
      boolean validate = false;
      boolean checkModel = false;
      boolean skipModelCheck = false;
      Path validatorWorkDir = null;

      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--help".equals(arg) || "-h".equals(arg)) {
          printUsageAndExit(0);
        } else if ("--project-root".equals(arg)) {
          projectRoot = Paths.get(requireValue(args, ++i, "--project-root"));
        } else if ("--template".equals(arg)) {
          templatePath = requireValue(args, ++i, "--template");
        } else if ("--model".equals(arg)) {
          modelPath = Paths.get(requireValue(args, ++i, "--model"));
        } else if ("--out".equals(arg)) {
          outputPath = Paths.get(requireValue(args, ++i, "--out"));
        } else if ("--validate".equals(arg)) {
          validate = true;
        } else if ("--check-model".equals(arg)) {
          checkModel = true;
        } else if ("--no-model-check".equals(arg)) {
          skipModelCheck = true;
        } else if ("--validator-work-dir".equals(arg)) {
          validatorWorkDir = Paths.get(requireValue(args, ++i, "--validator-work-dir"));
        } else if ("--validator-cache-dir".equals(arg)) {
          validatorWorkDir = Paths.get(requireValue(args, ++i, "--validator-cache-dir"));
        } else if (arg.startsWith("--")) {
          System.err.println("Unknown option: " + arg);
          printUsageAndExit(2);
        } else {
          System.err.println("Unexpected argument: " + arg);
          printUsageAndExit(2);
        }
      }

      if (modelPath == null) {
        System.err.println("Missing required option --model");
        printUsageAndExit(2);
      }

      return new CliOptions(
          projectRoot,
          templatePath,
          modelPath,
          outputPath,
          validate,
          checkModel,
          skipModelCheck,
          validatorWorkDir);
    }

    private static String requireValue(String[] args, int index, String option) {
      if (index >= args.length) {
        System.err.println("Missing value for " + option);
        printUsageAndExit(2);
      }
      return args[index];
    }

    private static void printUsageAndExit(int statusCode) {
      System.out.println(
          "Usage: java -jar velocity-renderer.jar --model FILE [--project-root DIR] [--template PATH] [--out FILE] [--validate] [--check-model] [--no-model-check] [--validator-work-dir DIR]\n"
              + "Defaults:\n"
              + "  --project-root  current working directory\n"
              + "  --template      " + DEFAULT_TEMPLATE + "\n"
              + "  --model         required, YAML or JSON with top-level xr\n"
              + "  --out           stdout\n"
              + "  --validate      off\n"
              + "  --no-model-check  off; render runs the schema check by default\n"
              + "  --check-model   validate the model against semantic-model/xrechnung.schema.json and exit\n"
              + "  --validator-work-dir  prototypes/velocity-renderer/target/validator-work\n"
              + "Aliases:\n"
              + "  --validator-cache-dir  deprecated alias for --validator-work-dir");
      System.exit(statusCode);
    }
  }
}

package local.xrechnung.velocityrunner;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * Minimal local runner for the public XRechnung Velocity templates.
 */
public final class VelocitySmokeRenderer {

  private VelocitySmokeRenderer() {
  }

  public static void main(String[] args) throws Exception {
    CliOptions options = CliOptions.parse(args);

    VelocityEngine engine = createEngine(options.projectRoot);
    Template template = engine.getTemplate(options.templatePath, StandardCharsets.UTF_8.name());

    VelocityContext context = new VelocityContext();
    context.put("xr", sampleModelForTemplate(options.templatePath));
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
              options.bundleZipPath,
              options.validatorCacheDir);
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
    Path tempDir = projectRoot.resolve("velocity-runner/target/rendered");
    Files.createDirectories(tempDir);
    Path templateName = Paths.get(templatePath).getFileName();
    String fileName = templateName == null ? "rendered-invoice.xml" : templateName.toString();
    if (fileName.endsWith(".vm")) {
      fileName = fileName.substring(0, fileName.length() - 3) + ".xml";
    }
    return tempDir.resolve(fileName);
  }

  private static Map<String, Object> sampleModelForTemplate(String templatePath) {
    String fileName = Paths.get(templatePath).getFileName().toString();
    if ("ubl-invoice-core.vm".equals(fileName)) {
      return SamplePublicInvoiceFactory.coreInvoice();
    }
    return SamplePublicInvoiceFactory.fullInvoice();
  }

  private static final class CliOptions {
    private static final String DEFAULT_TEMPLATE = "templates/ubl-invoice-full.vm";

    private final Path projectRoot;
    private final String templatePath;
    private final Path outputPath;
    private final boolean validate;
    private final Path bundleZipPath;
    private final Path validatorCacheDir;

    private CliOptions(
        Path projectRoot,
        String templatePath,
        Path outputPath,
        boolean validate,
        Path bundleZipPath,
        Path validatorCacheDir) {
      this.projectRoot = projectRoot;
      this.templatePath = templatePath;
      this.outputPath = outputPath;
      this.validate = validate;
      this.bundleZipPath = bundleZipPath;
      this.validatorCacheDir = validatorCacheDir;
    }

    private static CliOptions parse(String[] args) {
      Path projectRoot = Paths.get("").toAbsolutePath();
      String templatePath = DEFAULT_TEMPLATE;
      Path outputPath = null;
      boolean validate = false;
      Path bundleZipPath = null;
      Path validatorCacheDir = null;

      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--help".equals(arg) || "-h".equals(arg)) {
          printUsageAndExit(0);
        } else if ("--project-root".equals(arg)) {
          projectRoot = Paths.get(requireValue(args, ++i, "--project-root"));
        } else if ("--template".equals(arg)) {
          templatePath = requireValue(args, ++i, "--template");
        } else if ("--out".equals(arg)) {
          outputPath = Paths.get(requireValue(args, ++i, "--out"));
        } else if ("--validate".equals(arg)) {
          validate = true;
        } else if ("--bundle-zip".equals(arg)) {
          bundleZipPath = Paths.get(requireValue(args, ++i, "--bundle-zip"));
        } else if ("--validator-cache-dir".equals(arg)) {
          validatorCacheDir = Paths.get(requireValue(args, ++i, "--validator-cache-dir"));
        } else if (arg.startsWith("--")) {
          System.err.println("Unknown option: " + arg);
          printUsageAndExit(2);
        } else {
          templatePath = arg;
        }
      }

      return new CliOptions(
          projectRoot,
          templatePath,
          outputPath,
          validate,
          bundleZipPath,
          validatorCacheDir);
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
          "Usage: java -jar velocity-runner.jar [--project-root DIR] [--template PATH] [--out FILE] [--validate] [--bundle-zip FILE] [--validator-cache-dir DIR]\n"
              + "Defaults:\n"
              + "  --project-root  current working directory\n"
              + "  --template      " + DEFAULT_TEMPLATE + "\n"
              + "  --out           stdout\n"
              + "  --validate      off\n"
              + "  --bundle-zip    latest bundle-docs/xrechnung-3.0.2-bundle-*.zip\n"
              + "  --validator-cache-dir  velocity-runner/target/validator-cache");
      System.exit(statusCode);
    }
  }
}

package local.xrechnung.kositisolated.impl;

import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.api.XmlError;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.input.ByteArrayInput;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import local.xrechnung.kositisolated.bridge.VerifierBridge;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.sf.saxon.s9api.Processor;
import org.oclc.purl.dsdl.svrl.FailedAssert;
import org.oclc.purl.dsdl.svrl.Text;
import org.w3c.dom.Document;

public final class KositVerifierBridgeImpl implements VerifierBridge {

  private static final Path READY_MARKER =
      Path.of("resources/ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd");

  @Override
  public Map<String, Object> verify(Map<String, String> request) throws Exception {
    Path xmlPath = requirePath(request, "xmlPath");
    Path configPath = requirePath(request, "configPath");
    Path workDir = requirePath(request, "workDir");
    Path reportOut = requirePath(request, "reportOut");
    boolean diagnostics = Boolean.parseBoolean(request.get("diagnostics"));

    byte[] xmlBytes = Files.readAllBytes(xmlPath);
    PreparedConfiguration configuration = prepareConfiguration(configPath, workDir);

    Processor processor = ProcessorProvider.getProcessor();
    Configuration validatorConfiguration =
        Configuration.load(configuration.scenariosFile.toUri(), asDirectoryUri(configuration.configDirectory))
            .build(processor);
    DefaultCheck check = new DefaultCheck(processor, validatorConfiguration);
    Result result = check.checkInput(new ByteArrayInput(
        xmlBytes,
        xmlPath.toUri().toString(),
        "SHA-256"));

    Path writtenReport = null;
    if (result.getReportDocument() != null) {
      writeReport(result.getReportDocument(), reportOut);
      writtenReport = reportOut;
    }

    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("status", statusOf(result));
    output.put("xmlPath", xmlPath.toString());
    output.put("configPath", configPath.toString());
    output.put("workDir", workDir.toString());
    output.put("configurationDirectory", configuration.configDirectory.toString());
    output.put("reportPath", writtenReport != null ? writtenReport.toString() : null);
    output.put("acceptRecommendation", String.valueOf(result.getAcceptRecommendation()));
    output.put("processingSuccessful", result.isProcessingSuccessful());
    output.put("wellformed", result.isWellformed());
    output.put("schemaValid", result.isSchemaValid());
    output.put("schematronValid", result.isSchematronValid());
    output.put("messages", messagesOf(result));
    if (diagnostics) {
      output.put(
          "diagnostics",
          KositRuntimeDiagnostics.collect(KositVerifierBridgeImpl.class.getClassLoader()));
    }
    return output;
  }

  @Override
  public Map<String, Object> diagnostics(Map<String, String> request) {
    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("status", "DIAGNOSTICS");
    output.put("runtimeLibDir", request.get("runtimeLibDir"));
    output.put("messages", new ArrayList<Map<String, Object>>());
    output.put(
        "diagnostics",
        KositRuntimeDiagnostics.collect(KositVerifierBridgeImpl.class.getClassLoader()));
    return output;
  }

  private static String statusOf(Result result) {
    if (!result.isProcessingSuccessful()) {
      return "ERROR";
    }
    return result.isAcceptable() ? "ACCEPTED" : "REJECTED";
  }

  private static List<Map<String, Object>> messagesOf(Result result) {
    List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    for (String processingError : result.getProcessingErrors()) {
      Map<String, Object> message = baseMessage("processing", "error", processingError);
      messages.add(message);
    }
    for (XmlError schemaViolation : result.getSchemaViolations()) {
      Map<String, Object> message = baseMessage(
          "xsd",
          String.valueOf(schemaViolation.getSeverity()),
          schemaViolation.getMessage());
      message.put("line", schemaViolation.getRowNumber());
      message.put("column", schemaViolation.getColumnNumber());
      messages.add(message);
    }
    for (FailedAssert failedAssert : result.getFailedAsserts()) {
      Map<String, Object> message = baseMessage(
          "schematron",
          failedAssert.getFlag() != null ? failedAssert.getFlag() : "failed-assert",
          flattenText(failedAssert.getText()));
      message.put("location", failedAssert.getLocation());
      message.put("ruleId", failedAssert.getId());
      message.put("test", failedAssert.getTest());
      message.put("flag", failedAssert.getFlag());
      message.put("role", failedAssert.getRole());
      messages.add(message);
    }
    return messages;
  }

  private static Map<String, Object> baseMessage(String channel, String severity, String messageText) {
    Map<String, Object> message = new LinkedHashMap<String, Object>();
    message.put("channel", channel);
    message.put("severity", severity);
    message.put("message", messageText);
    return message;
  }

  private static String flattenText(Text text) {
    if (text == null || text.getContent() == null || text.getContent().isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (Object part : text.getContent()) {
      String chunk = String.valueOf(part).trim();
      if (!chunk.isEmpty()) {
        if (builder.length() > 0) {
          builder.append(' ');
        }
        builder.append(chunk);
      }
    }
    return builder.toString();
  }

  private static PreparedConfiguration prepareConfiguration(Path configZip, Path workDir)
      throws IOException {
    Files.createDirectories(workDir);
    Path configDirectory = workDir.resolve(stripZipSuffix(configZip.getFileName().toString()));
    extractZipIfNeeded(configZip, configDirectory, READY_MARKER);
    Path scenariosFile = configDirectory.resolve("scenarios.xml");
    if (!Files.isRegularFile(scenariosFile)) {
      throw new IOException("Missing scenarios.xml in " + configDirectory);
    }
    return new PreparedConfiguration(configDirectory.toAbsolutePath(), scenariosFile.toAbsolutePath());
  }

  private static void extractZipIfNeeded(
      Path zipPath,
      Path targetDir,
      Path markerRelativePath) throws IOException {
    // Spike shortcut: production should verify a bundle hash or manifest here.
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
         ZipInputStream zipStream = new ZipInputStream(rawStream, StandardCharsets.UTF_8)) {
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

  private static void writeReport(Document document, Path reportPath) throws Exception {
    Path parent = reportPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
    transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    try (OutputStream output = Files.newOutputStream(reportPath)) {
      transformer.transform(new DOMSource(document), new StreamResult(output));
    }
  }

  private static URI asDirectoryUri(Path directory) {
    String value = directory.toAbsolutePath().toUri().toString();
    return URI.create(value.endsWith("/") ? value : value + "/");
  }

  private static Path requirePath(Map<String, String> request, String key) {
    String value = request.get(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing bridge request value " + key);
    }
    return Path.of(value).toAbsolutePath().normalize();
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

  private static final class PreparedConfiguration {
    private final Path configDirectory;
    private final Path scenariosFile;

    private PreparedConfiguration(Path configDirectory, Path scenariosFile) {
      this.configDirectory = configDirectory;
      this.scenariosFile = scenariosFile;
    }
  }
}

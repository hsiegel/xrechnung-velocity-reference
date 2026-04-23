package local.xrechnung.saxonoutputverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class SaxonSchematronStage {

  private static final String SVRL_NS = "http://purl.oclc.org/dsdl/svrl";
  private static final QName INITIAL_DOCUMENT_URI =
      new QName("schxslt.validate.initial-document-uri");

  private SaxonSchematronStage() {
  }

  static StageRun run(
      Path xmlPath,
      Path configDirectory,
      ScenarioDefinition scenario,
      Path outputDir) throws Exception {
    Files.createDirectories(outputDir);
    Processor processor = new Processor(false);
    XsltCompiler compiler = processor.newXsltCompiler();

    List<SvrlResult> results = new ArrayList<SvrlResult>();
    List<VerifierFinding> allFindings = new ArrayList<VerifierFinding>();
    List<String> outputFiles = new ArrayList<String>();
    long totalFailedAsserts = 0L;
    int index = 1;
    for (String location : scenario.getSchematronLocations()) {
      Path stylesheet = configDirectory.resolve(location).normalize();
      if (!Files.exists(stylesheet)) {
        throw new IllegalArgumentException("Schematron XSLT not found: " + stylesheet);
      }

      String outputName =
          String.format("%02d-%s-svrl.xml", index, stripExtension(stylesheet.getFileName().toString()));
      Path outputPath = outputDir.resolve(outputName);
      runTransform(compiler, xmlPath, stylesheet, outputPath);

      SvrlResult result = summarize(location, stylesheet, outputPath);
      results.add(result);
      outputFiles.add(outputPath.toString());
      allFindings.addAll(result.getFindings());
      totalFailedAsserts += result.getFailedAsserts();
      index++;
    }

    return new StageRun(outputDir, outputFiles, results, allFindings, totalFailedAsserts);
  }

  private static void runTransform(
      XsltCompiler compiler,
      Path xmlPath,
      Path stylesheet,
      Path outputPath) throws Exception {
    XsltExecutable executable = compiler.compile(new StreamSource(stylesheet.toFile()));
    XsltTransformer transformer = executable.load();
    transformer.setSource(new StreamSource(xmlPath.toFile()));
    transformer.setParameter(INITIAL_DOCUMENT_URI, new XdmAtomicValue(xmlPath.toUri().toString()));

    Serializer serializer = executable.getProcessor().newSerializer(outputPath.toFile());
    serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
    serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    transformer.setDestination(serializer);
    transformer.transform();
  }

  private static SvrlResult summarize(
      String resourceLocation,
      Path stylesheet,
      Path outputPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(outputPath.toFile());

    List<VerifierFinding> findings = new ArrayList<VerifierFinding>();
    long failedAsserts = addFindings(
        findings,
        document.getElementsByTagNameNS(SVRL_NS, "failed-assert"),
        resourceLocation,
        "error");
    long successfulReports = addFindings(
        findings,
        document.getElementsByTagNameNS(SVRL_NS, "successful-report"),
        resourceLocation,
        "info");

    return new SvrlResult(
        resourceLocation,
        stylesheet,
        outputPath,
        failedAsserts,
        successfulReports,
        findings);
  }

  private static long addFindings(
      List<VerifierFinding> findings,
      NodeList nodes,
      String stepId,
      String fallbackSeverity) {
    long count = 0L;
    for (int index = 0; index < nodes.getLength(); index++) {
      Node node = nodes.item(index);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      findings.add(new VerifierFinding(
          "schematron",
          valueOrDefault(element.getAttribute("flag"), fallbackSeverity),
          firstText(element),
          emptyToNull(element.getAttribute("location")),
          firstNonEmpty(element.getAttribute("id"), element.getAttribute("see")),
          emptyToNull(element.getAttribute("test")),
          emptyToNull(element.getAttribute("flag")),
          emptyToNull(element.getAttribute("role")),
          element.getLocalName(),
          null,
          null,
          stepId));
      count++;
    }
    return count;
  }

  private static String firstText(Element element) {
    NodeList texts = element.getElementsByTagNameNS(SVRL_NS, "text");
    if (texts.getLength() > 0) {
      String text = texts.item(0).getTextContent();
      return text != null ? text.trim() : null;
    }
    String text = element.getTextContent();
    return text != null ? text.trim() : null;
  }

  private static String valueOrDefault(String value, String defaultValue) {
    String trimmed = emptyToNull(value);
    return trimmed != null ? trimmed : defaultValue;
  }

  private static String firstNonEmpty(String first, String second) {
    String firstValue = emptyToNull(first);
    if (firstValue != null) {
      return firstValue;
    }
    return emptyToNull(second);
  }

  private static String emptyToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  static final class StageRun {
    private final Path outputDirectory;
    private final List<String> outputFiles;
    private final List<SvrlResult> results;
    private final List<VerifierFinding> allFindings;
    private final long totalFailedAsserts;

    StageRun(
        Path outputDirectory,
        List<String> outputFiles,
        List<SvrlResult> results,
        List<VerifierFinding> allFindings,
        long totalFailedAsserts) {
      this.outputDirectory = outputDirectory;
      this.outputFiles = Collections.unmodifiableList(new ArrayList<String>(outputFiles));
      this.results = Collections.unmodifiableList(new ArrayList<SvrlResult>(results));
      this.allFindings = Collections.unmodifiableList(new ArrayList<VerifierFinding>(allFindings));
      this.totalFailedAsserts = totalFailedAsserts;
    }

    Path getOutputDirectory() {
      return outputDirectory;
    }

    List<String> getOutputFiles() {
      return outputFiles;
    }

    List<SvrlResult> getResults() {
      return results;
    }

    List<VerifierFinding> getAllFindings() {
      return allFindings;
    }

    long getTotalFailedAsserts() {
      return totalFailedAsserts;
    }
  }

  static final class SvrlResult {
    private final String resourceLocation;
    private final Path stylesheet;
    private final Path outputPath;
    private final long failedAsserts;
    private final long successfulReports;
    private final List<VerifierFinding> findings;

    SvrlResult(
        String resourceLocation,
        Path stylesheet,
        Path outputPath,
        long failedAsserts,
        long successfulReports,
        List<VerifierFinding> findings) {
      this.resourceLocation = resourceLocation;
      this.stylesheet = stylesheet;
      this.outputPath = outputPath;
      this.failedAsserts = failedAsserts;
      this.successfulReports = successfulReports;
      this.findings = Collections.unmodifiableList(new ArrayList<VerifierFinding>(findings));
    }

    String getResourceLocation() {
      return resourceLocation;
    }

    Path getStylesheet() {
      return stylesheet;
    }

    Path getOutputPath() {
      return outputPath;
    }

    long getFailedAsserts() {
      return failedAsserts;
    }

    long getSuccessfulReports() {
      return successfulReports;
    }

    List<VerifierFinding> getFindings() {
      return findings;
    }
  }
}

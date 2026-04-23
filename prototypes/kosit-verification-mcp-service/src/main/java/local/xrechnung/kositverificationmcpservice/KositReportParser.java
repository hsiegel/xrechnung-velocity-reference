package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class KositReportParser {

  private static final String REP_NS = "http://www.xoev.de/de/validator/varl/1";
  private static final String SCENARIO_NS = "http://www.xoev.de/de/validator/framework/1/scenarios";

  private KositReportParser() {
  }

  static ReportAnalysis analyze(byte[] reportXmlBytes) throws IOException {
    if (reportXmlBytes == null) {
      return ReportAnalysis.empty();
    }

    Document document;
    try {
      document = XmlSupport.parse(reportXmlBytes);
    } catch (SAXException e) {
      throw new IOException("Could not parse KoSIT report XML.", e);
    }

    Element root = document.getDocumentElement();
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("engineName", textAt(root, REP_NS, "engine", REP_NS, "name"));
    report.put("varlVersion", attribute(root, "varlVersion"));
    report.put("valid", booleanAttribute(root, "valid"));
    report.put("timestamp", XmlSupport.childText(root, REP_NS, "timestamp"));
    report.put("documentHash", textAt(root, REP_NS, "documentIdentification", REP_NS, "documentHash", REP_NS, "hashValue"));
    report.put("documentReference", textAt(root, REP_NS, "documentIdentification", REP_NS, "documentReference"));

    Element scenarioMatched = XmlSupport.firstChild(root, REP_NS, "scenarioMatched");
    Element noScenarioMatched = XmlSupport.firstChild(root, REP_NS, "noScenarioMatched");
    boolean matched = scenarioMatched != null;
    Element stepRoot = matched ? scenarioMatched : noScenarioMatched;
    String scenarioName = matched
        ? textAt(scenarioMatched, SCENARIO_NS, "scenario", SCENARIO_NS, "name")
        : null;
    String assessmentType = resolveAssessmentType(root);
    List<Map<String, Object>> stepResults = parseStepResults(stepRoot);
    report.put("assessmentType", assessmentType);
    report.put("validationStepResults", stepResults);

    List<Map<String, Object>> customFindings = new ArrayList<Map<String, Object>>();
    if (!matched) {
      customFindings.add(finding(
          "custom",
          "error",
          "No KoSIT validation scenario matched the document.",
          null,
          null,
          null,
          null,
          null,
          "noScenarioMatched",
          null,
          null,
          "scenario"));
    }

    return new ReportAnalysis(matched, scenarioName, report, stepResults, customFindings);
  }

  private static List<Map<String, Object>> parseStepResults(Element parent) {
    if (parent == null) {
      return Collections.emptyList();
    }
    List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) node;
        if ("validationStepResult".equals(element.getLocalName()) && REP_NS.equals(element.getNamespaceURI())) {
          steps.add(parseStepResult(element));
        }
      }
    }
    return steps;
  }

  private static Map<String, Object> parseStepResult(Element stepElement) {
    Map<String, Object> step = new LinkedHashMap<String, Object>();
    String stepId = attribute(stepElement, "id");
    step.put("id", stepId);
    step.put("valid", booleanAttribute(stepElement, "valid"));
    step.put("channel", channelForStep(stepId));

    Element resource = XmlSupport.firstChild(stepElement, SCENARIO_NS, "resource");
    if (resource != null) {
      step.put("resourceName", XmlSupport.childText(resource, SCENARIO_NS, "name"));
      step.put("resourceLocation", XmlSupport.childText(resource, SCENARIO_NS, "location"));
    } else {
      step.put("resourceName", null);
      step.put("resourceLocation", null);
    }

    List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    NodeList children = stepElement.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element child = (Element) node;
        if ("message".equals(child.getLocalName()) && REP_NS.equals(child.getNamespaceURI())) {
          messages.add(parseStepMessage(stepId, child));
        }
      }
    }
    step.put("messages", messages);
    return step;
  }

  private static Map<String, Object> parseStepMessage(String stepId, Element messageElement) {
    Integer line = integerAttribute(messageElement, "lineNumber");
    Integer column = integerAttribute(messageElement, "columnNumber");
    String xpathLocation = attribute(messageElement, "xpathLocation");
    String location = xpathLocation != null ? xpathLocation : lineColumnLocation(line, column);
    return finding(
        channelForStep(stepId),
        attribute(messageElement, "level"),
        XmlSupport.text(messageElement),
        location,
        attribute(messageElement, "code"),
        null,
        null,
        null,
        "report-message",
        line,
        column,
        stepId);
  }

  private static String resolveAssessmentType(Element root) {
    Element assessment = XmlSupport.firstChild(root, REP_NS, "assessment");
    if (assessment == null) {
      return null;
    }
    if (XmlSupport.firstChild(assessment, REP_NS, "accept") != null) {
      return "accept";
    }
    if (XmlSupport.firstChild(assessment, REP_NS, "reject") != null) {
      return "reject";
    }
    return null;
  }

  private static String textAt(Element root, String ns1, String local1, String ns2, String local2) {
    Element first = XmlSupport.firstChild(root, ns1, local1);
    return XmlSupport.childText(first, ns2, local2);
  }

  private static String textAt(
      Element root,
      String ns1,
      String local1,
      String ns2,
      String local2,
      String ns3,
      String local3) {
    Element first = XmlSupport.firstChild(root, ns1, local1);
    Element second = XmlSupport.firstChild(first, ns2, local2);
    return XmlSupport.childText(second, ns3, local3);
  }

  private static String attribute(Element element, String name) {
    return element != null && element.hasAttribute(name) ? element.getAttribute(name) : null;
  }

  private static Boolean booleanAttribute(Element element, String name) {
    if (element == null || !element.hasAttribute(name)) {
      return null;
    }
    return Boolean.valueOf(Boolean.parseBoolean(element.getAttribute(name)));
  }

  private static Integer integerAttribute(Element element, String name) {
    if (element == null || !element.hasAttribute(name)) {
      return null;
    }
    return Integer.valueOf(element.getAttribute(name));
  }

  private static String lineColumnLocation(Integer line, Integer column) {
    if (line == null) {
      return null;
    }
    return column != null ? "line " + line + ", column " + column : "line " + line;
  }

  private static String channelForStep(String stepId) {
    if (stepId == null) {
      return "custom";
    }
    if (stepId.startsWith("val-xsd")) {
      return "xsd";
    }
    if (stepId.startsWith("val-sch")) {
      return "schematron";
    }
    if (stepId.startsWith("val-xml")) {
      return "processing";
    }
    return "custom";
  }

  private static Map<String, Object> finding(
      String channel,
      String severity,
      String message,
      String location,
      String ruleId,
      String test,
      String flag,
      String role,
      String rawType,
      Integer line,
      Integer column,
      String stepId) {
    Map<String, Object> finding = new LinkedHashMap<String, Object>();
    finding.put("channel", channel);
    finding.put("severity", severity);
    finding.put("message", message);
    finding.put("location", location);
    finding.put("ruleId", ruleId);
    finding.put("test", test);
    finding.put("flag", flag);
    finding.put("role", role);
    finding.put("rawType", rawType);
    finding.put("line", line);
    finding.put("column", column);
    finding.put("stepId", stepId);
    return finding;
  }

  static final class ReportAnalysis {
    private final boolean scenarioMatched;
    private final String scenarioName;
    private final Map<String, Object> report;
    private final List<Map<String, Object>> stepResults;
    private final List<Map<String, Object>> customFindings;

    ReportAnalysis(
        boolean scenarioMatched,
        String scenarioName,
        Map<String, Object> report,
        List<Map<String, Object>> stepResults,
        List<Map<String, Object>> customFindings) {
      this.scenarioMatched = scenarioMatched;
      this.scenarioName = scenarioName;
      this.report = report;
      this.stepResults = stepResults;
      this.customFindings = customFindings;
    }

    static ReportAnalysis empty() {
      return new ReportAnalysis(false, null, Collections.<String, Object>emptyMap(),
          Collections.<Map<String, Object>>emptyList(),
          Collections.<Map<String, Object>>emptyList());
    }

    boolean isScenarioMatched() {
      return scenarioMatched;
    }

    String getScenarioName() {
      return scenarioName;
    }

    Map<String, Object> getReport() {
      return report;
    }

    List<Map<String, Object>> getStepResults() {
      return stepResults;
    }

    List<Map<String, Object>> getCustomFindings() {
      return customFindings;
    }
  }
}

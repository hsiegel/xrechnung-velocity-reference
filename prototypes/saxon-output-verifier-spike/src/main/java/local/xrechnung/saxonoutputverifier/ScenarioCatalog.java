package local.xrechnung.saxonoutputverifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class ScenarioCatalog {

  static final String INVOICE_SCENARIO_NAME = "EN16931 XRechnung (UBL Invoice)";
  private static final String SCENARIOS_NS =
      "http://www.xoev.de/de/validator/framework/1/scenarios";

  private final List<ScenarioDefinition> scenarios;

  private ScenarioCatalog(List<ScenarioDefinition> scenarios) {
    this.scenarios = scenarios;
  }

  static ScenarioCatalog load(Path scenariosFile) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(scenariosFile.toFile());

    Element root = document.getDocumentElement();
    List<ScenarioDefinition> scenarios = new ArrayList<ScenarioDefinition>();
    for (Element scenarioElement : childElements(root, SCENARIOS_NS, "scenario")) {
      scenarios.add(parseScenario(scenarioElement));
    }
    return new ScenarioCatalog(scenarios);
  }

  ScenarioDefinition requireScenario(String name) {
    for (ScenarioDefinition scenario : scenarios) {
      if (name.equals(scenario.getName())) {
        return scenario;
      }
    }
    throw new IllegalArgumentException("Scenario not found: " + name);
  }

  private static ScenarioDefinition parseScenario(Element scenarioElement) {
    String name = textOf(requireChild(scenarioElement, SCENARIOS_NS, "name"));
    String matchExpression = textOf(requireChild(scenarioElement, SCENARIOS_NS, "match"));
    Map<String, String> namespaces = new LinkedHashMap<String, String>();
    for (Element namespace : childElements(scenarioElement, SCENARIOS_NS, "namespace")) {
      namespaces.put(namespace.getAttribute("prefix"), textOf(namespace));
    }

    Element xmlSchemaElement = requireChild(scenarioElement, SCENARIOS_NS, "validateWithXmlSchema");
    String xmlSchemaLocation = resourceLocation(xmlSchemaElement);

    List<String> schematronLocations = new ArrayList<String>();
    for (Element schematron : childElements(scenarioElement, SCENARIOS_NS, "validateWithSchematron")) {
      schematronLocations.add(resourceLocation(schematron));
    }

    Element createReport = requireChild(scenarioElement, SCENARIOS_NS, "createReport");
    String reportLocation = resourceLocation(createReport);
    List<ScenarioDefinition.CustomLevel> customLevels =
        new ArrayList<ScenarioDefinition.CustomLevel>();
    for (Element customLevel : childElements(createReport, SCENARIOS_NS, "customLevel")) {
      customLevels.add(new ScenarioDefinition.CustomLevel(
          customLevel.getAttribute("level"),
          textOf(customLevel)));
    }

    return new ScenarioDefinition(
        name,
        matchExpression,
        namespaces,
        xmlSchemaLocation,
        schematronLocations,
        reportLocation,
        customLevels);
  }

  private static String resourceLocation(Element parent) {
    Element resource = requireChild(parent, SCENARIOS_NS, "resource");
    return textOf(requireChild(resource, SCENARIOS_NS, "location"));
  }

  private static Element requireChild(Element parent, String namespaceUri, String localName) {
    List<Element> children = childElements(parent, namespaceUri, localName);
    if (children.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing child element " + localName + " below " + parent.getLocalName());
    }
    return children.get(0);
  }

  private static List<Element> childElements(Element parent, String namespaceUri, String localName) {
    List<Element> result = new ArrayList<Element>();
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (node.getNodeType() == Node.ELEMENT_NODE
          && namespaceUri.equals(node.getNamespaceURI())
          && localName.equals(node.getLocalName())) {
        result.add((Element) node);
      }
    }
    return result;
  }

  private static String textOf(Element element) {
    return element.getTextContent().trim();
  }
}

package local.xrechnung.saxonoutputverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScenarioDefinition {

  private final String name;
  private final String matchExpression;
  private final Map<String, String> namespaces;
  private final String xmlSchemaLocation;
  private final List<String> schematronLocations;
  private final String reportLocation;
  private final List<CustomLevel> customLevels;

  ScenarioDefinition(
      String name,
      String matchExpression,
      Map<String, String> namespaces,
      String xmlSchemaLocation,
      List<String> schematronLocations,
      String reportLocation,
      List<CustomLevel> customLevels) {
    this.name = name;
    this.matchExpression = matchExpression;
    this.namespaces = Collections.unmodifiableMap(new LinkedHashMap<String, String>(namespaces));
    this.xmlSchemaLocation = xmlSchemaLocation;
    this.schematronLocations =
        Collections.unmodifiableList(new ArrayList<String>(schematronLocations));
    this.reportLocation = reportLocation;
    this.customLevels = Collections.unmodifiableList(new ArrayList<CustomLevel>(customLevels));
  }

  String getName() {
    return name;
  }

  String getMatchExpression() {
    return matchExpression;
  }

  Map<String, String> getNamespaces() {
    return namespaces;
  }

  String getXmlSchemaLocation() {
    return xmlSchemaLocation;
  }

  List<String> getSchematronLocations() {
    return schematronLocations;
  }

  String getReportLocation() {
    return reportLocation;
  }

  List<CustomLevel> getCustomLevels() {
    return customLevels;
  }

  static final class CustomLevel {
    private final String level;
    private final String codes;

    CustomLevel(String level, String codes) {
      this.level = level;
      this.codes = codes;
    }

    String getLevel() {
      return level;
    }

    String getCodes() {
      return codes;
    }
  }
}

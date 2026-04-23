package local.xrechnung.saxonoutputverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExpectedProfile {

  private static final String UBL_INVOICE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String XRECHNUNG_CUSTOMIZATION_ID =
      "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";
  private static final String PEPPOL_PROFILE_ID =
      "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
  private static final String INVOICE_XSD =
      "resources/ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd";
  private static final String EN16931_UBL_XSL =
      "resources/ubl/2.1/xsl/EN16931-UBL-validation.xsl";
  private static final String XRECHNUNG_UBL_XSL =
      "resources/xrechnung/3.0.2/xsl/XRechnung-UBL-validation.xsl";
  private static final String REPORT_XSL =
      "resources/xrechnung-report.xsl";

  private final String id;
  private final String scenarioName;
  private final String rootLocalName;
  private final String rootNamespaceUri;
  private final String customizationId;
  private final String profileId;
  private final String xmlSchemaLocation;
  private final List<String> schematronLocations;
  private final String reportLocation;

  private ExpectedProfile(
      String id,
      String scenarioName,
      String rootLocalName,
      String rootNamespaceUri,
      String customizationId,
      String profileId,
      String xmlSchemaLocation,
      List<String> schematronLocations,
      String reportLocation) {
    this.id = id;
    this.scenarioName = scenarioName;
    this.rootLocalName = rootLocalName;
    this.rootNamespaceUri = rootNamespaceUri;
    this.customizationId = customizationId;
    this.profileId = profileId;
    this.xmlSchemaLocation = xmlSchemaLocation;
    this.schematronLocations =
        Collections.unmodifiableList(new ArrayList<String>(schematronLocations));
    this.reportLocation = reportLocation;
  }

  static ExpectedProfile xrechnungUblInvoice() {
    return new ExpectedProfile(
        ProfileRegistry.DEFAULT_PROFILE_ID,
        ScenarioCatalog.INVOICE_SCENARIO_NAME,
        "Invoice",
        UBL_INVOICE_NS,
        XRECHNUNG_CUSTOMIZATION_ID,
        PEPPOL_PROFILE_ID,
        INVOICE_XSD,
        asList(EN16931_UBL_XSL, XRECHNUNG_UBL_XSL),
        REPORT_XSL);
  }

  String getId() {
    return id;
  }

  String getScenarioName() {
    return scenarioName;
  }

  String getRootLocalName() {
    return rootLocalName;
  }

  String getRootNamespaceUri() {
    return rootNamespaceUri;
  }

  String getCustomizationId() {
    return customizationId;
  }

  String getProfileId() {
    return profileId;
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

  List<String> validateScenarioDefinition(ScenarioDefinition scenario) {
    List<String> mismatches = new ArrayList<String>();
    if (!scenarioName.equals(scenario.getName())) {
      mismatches.add("Scenario name does not match the expected profile contract.");
    }
    if (!xmlSchemaLocation.equals(scenario.getXmlSchemaLocation())) {
      mismatches.add("XML Schema location differs from the expected profile contract.");
    }
    if (!schematronLocations.equals(scenario.getSchematronLocations())) {
      mismatches.add("Schematron order differs from the expected profile contract.");
    }
    if (!reportLocation.equals(scenario.getReportLocation())) {
      mismatches.add("Report stylesheet location differs from the expected profile contract.");
    }
    return Collections.unmodifiableList(mismatches);
  }

  private static List<String> asList(String first, String second) {
    List<String> values = new ArrayList<String>();
    values.add(first);
    values.add(second);
    return values;
  }
}

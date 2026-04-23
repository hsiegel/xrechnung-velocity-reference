package local.xrechnung.saxonoutputverifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

public class SchemaValidationStageTest {

  @Test
  public void failsInvalidXmlAgainstConfiguredSchema() throws Exception {
    Path configDir = Files.createTempDirectory("saxon-xsd-");
    Path schemaFile = configDir.resolve("invoice.xsd");
    Files.write(schemaFile, simpleSchema().getBytes(StandardCharsets.UTF_8));

    Path xmlFile = Files.createTempFile("saxon-invalid-", ".xml");
    Files.write(xmlFile, "<Invoice/>".getBytes(StandardCharsets.UTF_8));

    ScenarioDefinition scenario = new ScenarioDefinition(
        "test",
        "true()",
        Collections.<String, String>emptyMap(),
        "invoice.xsd",
        Collections.<String>emptyList(),
        "report.xsl",
        Collections.<ScenarioDefinition.CustomLevel>emptyList());

    SchemaValidationStage.Result result =
        SchemaValidationStage.validate(configDir, scenario, xmlFile);

    assertFalse(result.isValid());
    assertFalse(result.getFindings().isEmpty());
    assertNotNull(result.getFindings().get(0).getLine());
  }

  @Test
  public void acceptsValidXmlAgainstConfiguredSchema() throws Exception {
    Path configDir = Files.createTempDirectory("saxon-xsd-");
    Path schemaFile = configDir.resolve("invoice.xsd");
    Files.write(schemaFile, simpleSchema().getBytes(StandardCharsets.UTF_8));

    Path xmlFile = Files.createTempFile("saxon-valid-", ".xml");
    Files.write(xmlFile,
        "<Invoice><CustomizationID>ok</CustomizationID></Invoice>".getBytes(StandardCharsets.UTF_8));

    ScenarioDefinition scenario = new ScenarioDefinition(
        "test",
        "true()",
        Collections.<String, String>emptyMap(),
        "invoice.xsd",
        Collections.<String>emptyList(),
        "report.xsl",
        Collections.<ScenarioDefinition.CustomLevel>emptyList());

    SchemaValidationStage.Result result =
        SchemaValidationStage.validate(configDir, scenario, xmlFile);

    assertTrue(result.isValid());
    assertTrue(result.getFindings().isEmpty());
  }

  private static String simpleSchema() {
    return ""
        + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<xs:element name=\"Invoice\">"
        + "<xs:complexType>"
        + "<xs:sequence>"
        + "<xs:element name=\"CustomizationID\" type=\"xs:string\"/>"
        + "</xs:sequence>"
        + "</xs:complexType>"
        + "</xs:element>"
        + "</xs:schema>";
  }
}

package local.xrechnung.kositverificationmcpservice;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;

public class ValidationInputResolverTest {

  @Test
  public void resolvesRelativeXmlPathFromProjectRoot() throws Exception {
    Path projectRoot = Files.createTempDirectory("kosit-verification-input-root");
    Path xmlFile = projectRoot.resolve("examples/test.xml");
    Files.createDirectories(xmlFile.getParent());
    byte[] xmlBytes = "<Invoice/>".getBytes(UTF_8);
    Files.write(xmlFile, xmlBytes);

    ValidationInput input = ValidationInputResolver.resolve(
        projectRoot,
        Map.of(
            "xmlPath", "examples/test.xml",
            "persistArtifacts", Boolean.TRUE));

    assertEquals("xmlPath", input.getSource());
    assertEquals("test.xml", input.getInputName());
    assertEquals(xmlFile, input.getResolvedPath());
    assertEquals(xmlFile.toUri().toString(), input.getDocumentReference());
    assertArrayEquals(xmlBytes, input.getXmlBytes());
    assertTrue(input.isPersistArtifacts());
  }

  @Test
  public void resolvesInlineXmlWithDefaultInputName() throws Exception {
    ValidationInput input = ValidationInputResolver.resolve(
        Path.of("."),
        Map.of("xmlContent", "<Invoice/>"));

    assertEquals("xmlContent", input.getSource());
    assertEquals("inline-input.xml", input.getInputName());
    assertNull(input.getResolvedPath());
    assertEquals("inline-input.xml", input.getDocumentReference());
    assertEquals("<Invoice/>", new String(input.getXmlBytes(), UTF_8));
    assertFalse(input.isPersistArtifacts());
  }

  @Test
  public void rejectsBothXmlPathAndXmlContent() throws Exception {
    try {
      ValidationInputResolver.resolve(
          Path.of("."),
          Map.of(
              "xmlPath", "invoice.xml",
              "xmlContent", "<Invoice/>"));
      fail("Expected ToolFailure");
    } catch (ToolFailure e) {
      assertEquals("input_invalid", e.getCategory());
      assertTrue(e.getMessage().contains("Exactly one of xmlPath or xmlContent"));
    }
  }

  @Test
  public void rejectsInvalidPersistArtifactsFlag() throws Exception {
    try {
      ValidationInputResolver.resolve(
          Path.of("."),
          Map.of(
              "xmlContent", "<Invoice/>",
              "persistArtifacts", "sometimes"));
      fail("Expected ToolFailure");
    } catch (ToolFailure e) {
      assertEquals("input_invalid", e.getCategory());
      assertTrue(e.getMessage().contains("persistArtifacts must be a boolean"));
    }
  }
}

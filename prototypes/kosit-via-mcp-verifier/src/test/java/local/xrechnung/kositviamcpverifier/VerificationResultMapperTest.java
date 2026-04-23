package local.xrechnung.kositviamcpverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class VerificationResultMapperTest {

  @Test
  public void mapsAcceptedServiceResult() {
    VerificationResult result = new VerificationResultMapper(
        JsonSupport.createObjectMapper(),
        false)
        .fromStructuredContent(Map.of(
            "processingSuccessful", Boolean.TRUE,
            "acceptRecommendation", "ACCEPTABLE",
            "scenario", Map.of("name", "EN16931 XRechnung (UBL Invoice)"),
            "summary", Map.of("findingCountTotal", Integer.valueOf(0)),
            "findings", List.of(),
            "artifacts", Map.of("persisted", Boolean.FALSE)));

    assertTrue(result.isProcessingSuccessful());
    assertTrue(result.isAccepted());
    assertEquals("ACCEPTABLE", result.getAcceptRecommendation());
    assertEquals(Integer.valueOf(0), result.getSummary().getFindingCountTotal());
  }

  @Test
  public void mapsRejectedServiceResultAsBusinessFailure() {
    VerificationResult result = new VerificationResultMapper(
        JsonSupport.createObjectMapper(),
        false)
        .fromStructuredContent(Map.of(
            "processingSuccessful", Boolean.TRUE,
            "acceptRecommendation", "REJECT",
            "summary", Map.of("findingCountTotal", Integer.valueOf(1)),
            "findings", List.of(Map.of(
                "channel", "schematron",
                "severity", "fatal",
                "message", "Broken rule"))));

    assertTrue(result.isProcessingSuccessful());
    assertFalse(result.isAccepted());
    assertFalse(result.isTechnicalFailure());
    assertEquals(1, result.getFindings().size());
  }

  @Test
  public void mapsToolErrorAsTechnicalFailure() {
    VerificationResult result = new VerificationResultMapper(
        JsonSupport.createObjectMapper(),
        false)
        .fromStructuredContent(Map.of(
            "processingSuccessful", Boolean.FALSE,
            "toolError", Map.of(
                "category", "input_unreadable",
                "message", "XML file not found")));

    assertFalse(result.isProcessingSuccessful());
    assertTrue(result.isTechnicalFailure());
    assertNotNull(result.getTechnicalFailure());
    assertEquals("input_unreadable", result.getTechnicalFailure().getCategory());
  }
}

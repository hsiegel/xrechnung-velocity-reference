package local.xrechnung.saxonoutputverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ProfileGateTest {

  @Test
  public void matchesExpectedInvoiceProfile() {
    ExpectedProfile profile = ExpectedProfile.xrechnungUblInvoice();
    InvoiceInspector.DocumentInfo documentInfo = new InvoiceInspector.DocumentInfo(
        profile.getRootLocalName(),
        profile.getRootNamespaceUri(),
        profile.getCustomizationId(),
        profile.getProfileId());

    ProfileGate.Result result = ProfileGate.evaluate(documentInfo, profile, true);

    assertTrue(result.matches());
    assertTrue(result.getMismatches().isEmpty());
  }

  @Test
  public void reportsEachMismatchType() {
    ExpectedProfile profile = ExpectedProfile.xrechnungUblInvoice();
    InvoiceInspector.DocumentInfo documentInfo = new InvoiceInspector.DocumentInfo(
        "CreditNote",
        "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
        "wrong-customization",
        "wrong-profile");

    ProfileGate.Result result = ProfileGate.evaluate(documentInfo, profile, false);
    List<ProfileGate.Mismatch> mismatches = result.getMismatches();

    assertFalse(result.matches());
    assertEquals(4, mismatches.size());
    assertEquals("root_mismatch", mismatches.get(0).getCode());
    assertEquals("customization_id_mismatch", mismatches.get(1).getCode());
    assertEquals("profile_id_mismatch", mismatches.get(2).getCode());
    assertEquals("scenario_match_mismatch", mismatches.get(3).getCode());
  }
}

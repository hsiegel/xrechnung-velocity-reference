package local.xrechnung.kositverificationmcpservice;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InvoiceScopeInspectorTest {

  private static final String UBL_INVOICE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String UBL_CREDIT_NOTE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
  private static final String CBC_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

  @Test
  public void acceptsBaseUblInvoiceScope() throws Exception {
    InvoiceScopeInspector.ScopeCheck scope = InvoiceScopeInspector.inspect(invoiceXml(
        "Invoice",
        UBL_INVOICE_NS,
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0")
        .getBytes(UTF_8));

    assertTrue(scope.isSupported());
    assertNull(scope.getMessage());
    assertEquals(
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
        scope.getCustomizationId());
  }

  @Test
  public void rejectsCreditNote() throws Exception {
    InvoiceScopeInspector.ScopeCheck scope = InvoiceScopeInspector.inspect(invoiceXml(
        "CreditNote",
        UBL_CREDIT_NOTE_NS,
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0")
        .getBytes(UTF_8));

    assertFalse(scope.isSupported());
    assertTrue(scope.getMessage().contains("UBL CreditNote"));
  }

  @Test
  public void rejectsExtensionProfile() throws Exception {
    InvoiceScopeInspector.ScopeCheck scope = InvoiceScopeInspector.inspect(invoiceXml(
        "Invoice",
        UBL_INVOICE_NS,
        "urn:cen.eu:en16931:2017#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0")
        .getBytes(UTF_8));

    assertFalse(scope.isSupported());
    assertTrue(scope.getMessage().contains("Extension"));
  }

  @Test
  public void allowsUnknownCustomizationIdToReachValidator() throws Exception {
    InvoiceScopeInspector.ScopeCheck scope = InvoiceScopeInspector.inspect(invoiceXml(
        "Invoice",
        UBL_INVOICE_NS,
        "urn:example:custom-profile")
        .getBytes(UTF_8));

    assertTrue(scope.isSupported());
    assertEquals("urn:example:custom-profile", scope.getCustomizationId());
  }

  private static String invoiceXml(String localName, String namespace, String customizationId) {
    return ""
        + "<" + localName
        + " xmlns=\"" + namespace + "\""
        + " xmlns:cbc=\"" + CBC_NS + "\">"
        + "<cbc:CustomizationID>" + customizationId + "</cbc:CustomizationID>"
        + "</" + localName + ">";
  }
}

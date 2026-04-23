package local.xrechnung.kositembeddedverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class InvoiceScopeInspectorTest {

  @Test
  public void supportsBaseUblInvoice() throws IOException {
    InvoiceScopeInspector.ScopeCheck check = InvoiceScopeInspector.inspect(xml(
        "Invoice",
        "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"));

    assertTrue(check.isSupported());
    assertNull(check.getMessage());
    assertEquals(
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
        check.getCustomizationId());
  }

  @Test
  public void rejectsCreditNote() throws IOException {
    InvoiceScopeInspector.ScopeCheck check = InvoiceScopeInspector.inspect(xml(
        "CreditNote",
        "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
        null));

    assertFalse(check.isSupported());
    assertTrue(check.getMessage().contains("UBL Invoice"));
  }

  @Test
  public void rejectsExtensionInvoice() throws IOException {
    InvoiceScopeInspector.ScopeCheck check = InvoiceScopeInspector.inspect(xml(
        "Invoice",
        "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"
            + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0"));

    assertFalse(check.isSupported());
    assertTrue(check.getMessage().contains("Extension"));
  }

  private static byte[] xml(String rootName, String namespace, String customizationId) {
    StringBuilder builder = new StringBuilder();
    builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    builder.append("<InvoiceOrOther xmlns=\"").append(namespace).append("\" ");
    builder.append("xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">");
    if (customizationId != null) {
      builder.append("<cbc:CustomizationID>").append(customizationId).append("</cbc:CustomizationID>");
    }
    builder.append("</InvoiceOrOther>");
    String xml = builder.toString()
        .replace("InvoiceOrOther", rootName);
    return xml.getBytes(StandardCharsets.UTF_8);
  }
}

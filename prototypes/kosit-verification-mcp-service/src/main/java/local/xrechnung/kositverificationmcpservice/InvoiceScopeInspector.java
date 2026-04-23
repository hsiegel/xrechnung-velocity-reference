package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

final class InvoiceScopeInspector {

  private static final String UBL_INVOICE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String UBL_CREDIT_NOTE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
  private static final String CII_NS =
      "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
  private static final String CBC_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
  private static final String EXTENSION_FRAGMENT =
      "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0";
  private static final String CVD_FRAGMENT =
      "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_";

  private InvoiceScopeInspector() {
  }

  static ScopeCheck inspect(byte[] xmlBytes) throws IOException {
    Document document;
    try {
      document = XmlSupport.parse(xmlBytes);
    } catch (SAXException e) {
      throw new IOException("Could not inspect the XML scope before validation: " + e.getMessage(), e);
    }

    Element root = document.getDocumentElement();
    if (root == null) {
      throw new IOException("The XML has no document element.");
    }

    String namespace = root.getNamespaceURI();
    String localName = root.getLocalName();
    String customizationId = XmlSupport.childText(root, CBC_NS, "CustomizationID");

    if ("CreditNote".equals(localName) && UBL_CREDIT_NOTE_NS.equals(namespace)) {
      return ScopeCheck.unsupported(
          "This prototype only covers XRechnung 3.0.2 as UBL Invoice. "
              + "Found UBL CreditNote instead.",
          customizationId);
    }
    if ("CrossIndustryInvoice".equals(localName) && CII_NS.equals(namespace)) {
      return ScopeCheck.unsupported(
          "This prototype only covers XRechnung 3.0.2 as UBL Invoice. "
              + "Found CII instead.",
          customizationId);
    }
    if (!"Invoice".equals(localName) || !UBL_INVOICE_NS.equals(namespace)) {
      return ScopeCheck.unsupported(
          "This prototype only covers XRechnung 3.0.2 as UBL Invoice. "
              + "Found root element " + describeRoot(namespace, localName) + ".",
          customizationId);
    }
    if (customizationId != null && customizationId.contains(EXTENSION_FRAGMENT)) {
      return ScopeCheck.unsupported(
          "This prototype only covers the base XRechnung UBL Invoice profile. "
              + "XRechnung Extension invoices are out of scope.",
          customizationId);
    }
    if (customizationId != null && customizationId.contains(CVD_FRAGMENT)) {
      return ScopeCheck.unsupported(
          "This prototype only covers the base XRechnung UBL Invoice profile. "
              + "XRechnung CVD invoices are out of scope.",
          customizationId);
    }
    return ScopeCheck.supported(customizationId);
  }

  private static String describeRoot(String namespace, String localName) {
    String name = localName != null ? localName : "<unknown>";
    if (namespace == null || namespace.isEmpty()) {
      return name;
    }
    return name + " in namespace " + namespace;
  }

  static final class ScopeCheck {
    private final boolean supported;
    private final String message;
    private final String customizationId;

    private ScopeCheck(boolean supported, String message, String customizationId) {
      this.supported = supported;
      this.message = message;
      this.customizationId = customizationId;
    }

    static ScopeCheck supported(String customizationId) {
      return new ScopeCheck(true, null, customizationId);
    }

    static ScopeCheck unsupported(String message, String customizationId) {
      return new ScopeCheck(false, message, customizationId);
    }

    boolean isSupported() {
      return supported;
    }

    String getMessage() {
      return message;
    }

    String getCustomizationId() {
      return customizationId;
    }
  }
}

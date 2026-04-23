package local.xrechnung.kositembeddedverifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
      document = parse(xmlBytes);
    } catch (ParserConfigurationException e) {
      throw new IOException("Could not configure XML parser for scope inspection.", e);
    } catch (SAXException e) {
      return ScopeCheck.supported(
          "Could not inspect the XML scope before validation: " + e.getMessage(),
          null);
    }

    Element root = document.getDocumentElement();
    if (root == null) {
      return ScopeCheck.supported(
          "The XML has no document element. Proceeding with validator.",
          null);
    }

    String namespace = root.getNamespaceURI();
    String localName = root.getLocalName();
    String customizationId = findDirectChildText(root, CBC_NS, "CustomizationID");

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
    return ScopeCheck.supported(null, customizationId);
  }

  private static Document parse(byte[] xmlBytes)
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setExpandEntityReferences(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
    setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(new ByteArrayInputStream(xmlBytes));
  }

  private static void setFeatureIfSupported(
      DocumentBuilderFactory factory,
      String feature,
      boolean value) throws ParserConfigurationException {
    factory.setFeature(feature, value);
  }

  private static String findDirectChildText(Element parent, String namespace, String localName) {
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) child;
        if (localName.equals(element.getLocalName()) && namespace.equals(element.getNamespaceURI())) {
          String value = element.getTextContent();
          return value != null ? value.trim() : null;
        }
      }
    }
    return null;
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

    static ScopeCheck supported(String message, String customizationId) {
      return new ScopeCheck(true, message, customizationId);
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

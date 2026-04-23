package local.xrechnung.saxonoutputverifier;

import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class InvoiceInspector {

  private static final String UBL_INVOICE_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String CBC_NS =
      "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

  private InvoiceInspector() {
  }

  static DocumentInfo inspect(Path xmlPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(xmlPath.toFile());
    Element root = document.getDocumentElement();

    return new DocumentInfo(
        root.getLocalName(),
        root.getNamespaceURI(),
        firstValue(root, "CustomizationID"),
        firstValue(root, "ProfileID"));
  }

  private static String firstValue(Element root, String localName) {
    NodeList nodes = root.getElementsByTagNameNS(CBC_NS, localName);
    if (nodes.getLength() == 0) {
      return null;
    }
    String value = nodes.item(0).getTextContent();
    return value != null ? value.trim() : null;
  }

  static final class DocumentInfo {
    private final String rootLocalName;
    private final String rootNamespaceUri;
    private final String customizationId;
    private final String profileId;

    DocumentInfo(
        String rootLocalName,
        String rootNamespaceUri,
        String customizationId,
        String profileId) {
      this.rootLocalName = rootLocalName;
      this.rootNamespaceUri = rootNamespaceUri;
      this.customizationId = customizationId;
      this.profileId = profileId;
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

    boolean isUblInvoice() {
      return "Invoice".equals(rootLocalName) && UBL_INVOICE_NS.equals(rootNamespaceUri);
    }
  }
}

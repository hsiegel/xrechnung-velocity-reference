package local.xrechnung.kositverificationmcpservice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class XmlSupport {

  private XmlSupport() {
  }

  static Document parse(byte[] xmlBytes) throws IOException, SAXException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
      setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
      setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xmlBytes));
    } catch (ParserConfigurationException e) {
      throw new IOException("Could not configure XML parser.", e);
    }
  }

  static byte[] toBytes(Document document) throws IOException {
    try {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(document), new StreamResult(output));
      return output.toByteArray();
    } catch (TransformerException e) {
      throw new IOException("Could not serialize XML document.", e);
    }
  }

  static String sha256Base64(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing SHA-256 support.", e);
    }
  }

  static String toUtf8(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  static Element firstChild(Element parent, String namespace, String localName) {
    if (parent == null) {
      return null;
    }
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) child;
        if (localName.equals(element.getLocalName()) && namespace.equals(element.getNamespaceURI())) {
          return element;
        }
      }
    }
    return null;
  }

  static String childText(Element parent, String namespace, String localName) {
    Element child = firstChild(parent, namespace, localName);
    return child != null ? text(child) : null;
  }

  static String text(Element element) {
    if (element == null) {
      return null;
    }
    String value = element.getTextContent();
    return value != null ? value.trim() : null;
  }

  static XPath newXPath(NamespaceContext namespaceContext) {
    XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(namespaceContext);
    return xpath;
  }

  private static void setFeatureIfSupported(
      DocumentBuilderFactory factory,
      String feature,
      boolean value) throws ParserConfigurationException {
    factory.setFeature(feature, value);
  }
}

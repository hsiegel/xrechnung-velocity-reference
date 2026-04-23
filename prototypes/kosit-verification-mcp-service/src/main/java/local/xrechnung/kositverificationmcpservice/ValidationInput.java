package local.xrechnung.kositverificationmcpservice;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ValidationInput {

  private final String source;
  private final String inputName;
  private final Path resolvedPath;
  private final String documentReference;
  private final byte[] xmlBytes;
  private final boolean persistArtifacts;

  ValidationInput(
      String source,
      String inputName,
      Path resolvedPath,
      String documentReference,
      byte[] xmlBytes,
      boolean persistArtifacts) {
    this.source = source;
    this.inputName = inputName;
    this.resolvedPath = resolvedPath;
    this.documentReference = documentReference;
    this.xmlBytes = xmlBytes;
    this.persistArtifacts = persistArtifacts;
  }

  String getSource() {
    return source;
  }

  String getInputName() {
    return inputName;
  }

  Path getResolvedPath() {
    return resolvedPath;
  }

  String getDocumentReference() {
    return documentReference;
  }

  byte[] getXmlBytes() {
    return xmlBytes;
  }

  boolean isPersistArtifacts() {
    return persistArtifacts;
  }

  Map<String, Object> toInputBlock() {
    Map<String, Object> block = new LinkedHashMap<String, Object>();
    block.put("source", source);
    block.put("inputName", inputName);
    block.put("resolvedPath", resolvedPath != null ? resolvedPath.toString() : null);
    block.put("sha256Base64", XmlSupport.sha256Base64(xmlBytes));
    block.put("persistArtifacts", persistArtifacts);
    return block;
  }
}

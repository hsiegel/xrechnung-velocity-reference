package local.xrechnung.kositviamcpverifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VerificationRequest {

  private final String xmlPath;
  private final String xmlContent;
  private final String inputName;
  private final boolean persistArtifacts;

  private VerificationRequest(
      String xmlPath,
      String xmlContent,
      String inputName,
      boolean persistArtifacts) {
    this.xmlPath = xmlPath;
    this.xmlContent = xmlContent;
    this.inputName = inputName;
    this.persistArtifacts = persistArtifacts;
  }

  public static VerificationRequest forXmlPath(String xmlPath, boolean persistArtifacts) {
    return new VerificationRequest(xmlPath, null, null, persistArtifacts);
  }

  public static VerificationRequest forXmlContent(
      String xmlContent,
      String inputName,
      boolean persistArtifacts) {
    return new VerificationRequest(null, xmlContent, inputName, persistArtifacts);
  }

  public String getXmlPath() {
    return xmlPath;
  }

  public String getXmlContent() {
    return xmlContent;
  }

  public String getInputName() {
    return inputName;
  }

  public boolean isPersistArtifacts() {
    return persistArtifacts;
  }

  Map<String, Object> toToolArguments() {
    Map<String, Object> arguments = new LinkedHashMap<String, Object>();
    if (xmlPath != null) {
      arguments.put("xmlPath", xmlPath);
    } else {
      arguments.put("xmlContent", xmlContent);
      if (inputName != null) {
        arguments.put("inputName", inputName);
      }
    }
    arguments.put("persistArtifacts", Boolean.valueOf(persistArtifacts));
    return arguments;
  }
}

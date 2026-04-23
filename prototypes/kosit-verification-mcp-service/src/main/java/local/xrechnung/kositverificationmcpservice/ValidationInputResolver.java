package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

final class ValidationInputResolver {

  private ValidationInputResolver() {
  }

  static ValidationInput resolve(Path projectRoot, Map<String, Object> arguments) throws ToolFailure {
    String xmlPathValue = stringValue(arguments.get("xmlPath"));
    String xmlContentValue = stringValue(arguments.get("xmlContent"));
    String inputNameValue = stringValue(arguments.get("inputName"));
    boolean persistArtifacts = booleanValue(arguments.get("persistArtifacts"));

    boolean hasPath = xmlPathValue != null && !xmlPathValue.isEmpty();
    boolean hasContent = xmlContentValue != null && !xmlContentValue.isEmpty();
    if (hasPath == hasContent) {
      throw new ToolFailure(
          "input_invalid",
          "Exactly one of xmlPath or xmlContent must be provided.",
          Map.of(
              "xmlPathProvided", Boolean.valueOf(hasPath),
              "xmlContentProvided", Boolean.valueOf(hasContent)));
    }

    if (hasPath) {
      Path resolvedPath = resolvePath(projectRoot, xmlPathValue);
      if (!Files.isRegularFile(resolvedPath)) {
        throw new ToolFailure(
            "input_unreadable",
            "XML file not found: " + resolvedPath,
            Map.of("resolvedPath", resolvedPath.toString()));
      }
      try {
        byte[] xmlBytes = Files.readAllBytes(resolvedPath);
        return new ValidationInput(
            "xmlPath",
            resolvedPath.getFileName().toString(),
            resolvedPath,
            resolvedPath.toUri().toString(),
            xmlBytes,
            persistArtifacts);
      } catch (IOException e) {
        throw new ToolFailure(
            "input_unreadable",
            "Could not read XML file: " + resolvedPath,
            Map.of("resolvedPath", resolvedPath.toString()),
            e);
      }
    }

    String inputName = inputNameValue != null && !inputNameValue.isEmpty()
        ? inputNameValue
        : "inline-input.xml";
    return new ValidationInput(
        "xmlContent",
        inputName,
        null,
        inputName,
        xmlContentValue.getBytes(StandardCharsets.UTF_8),
        persistArtifacts);
  }

  static Map<String, Object> describeRequestedInput(Map<String, Object> arguments) {
    Map<String, Object> block = new LinkedHashMap<String, Object>();
    String xmlPathValue = stringValue(arguments.get("xmlPath"));
    String xmlContentValue = stringValue(arguments.get("xmlContent"));
    String inputNameValue = stringValue(arguments.get("inputName"));
    boolean persistArtifacts = false;
    try {
      persistArtifacts = booleanValue(arguments.get("persistArtifacts"));
    } catch (ToolFailure ignored) {
    }
    String source = xmlPathValue != null && !xmlPathValue.isEmpty()
        ? "xmlPath"
        : xmlContentValue != null && !xmlContentValue.isEmpty() ? "xmlContent" : "unknown";
    block.put("source", source);
    block.put("inputName", inputNameValue);
    block.put("resolvedPath", xmlPathValue);
    block.put("sha256Base64", null);
    block.put("persistArtifacts", Boolean.valueOf(persistArtifacts));
    return block;
  }

  private static Path resolvePath(Path projectRoot, String xmlPathValue) {
    Path path = Paths.get(xmlPathValue);
    if (path.isAbsolute()) {
      return path.normalize();
    }
    return projectRoot.resolve(path).normalize();
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static boolean booleanValue(Object value) throws ToolFailure {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    String text = String.valueOf(value).trim();
    if ("true".equalsIgnoreCase(text)) {
      return true;
    }
    if ("false".equalsIgnoreCase(text)) {
      return false;
    }
    throw new ToolFailure(
        "input_invalid",
        "persistArtifacts must be a boolean value.",
        Map.of("persistArtifacts", value));
  }
}

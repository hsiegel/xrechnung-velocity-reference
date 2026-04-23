package local.xrechnung.kositverificationmcpservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class McpSchemaSupport {

  private McpSchemaSupport() {
  }

  static String inputSchemaJson(ObjectMapper objectMapper) {
    return JsonSupport.toJsonString(objectMapper, inputSchemaDefinition());
  }

  static String outputSchemaJson(ObjectMapper objectMapper) {
    return JsonSupport.toJsonString(objectMapper, outputSchemaDefinition());
  }

  private static Map<String, Object> inputSchemaDefinition() {
    Map<String, Object> schema = objectSchema();
    Map<String, Object> properties = properties(schema);
    properties.put("xmlPath", stringSchema(
        "Path to the XML file. Relative paths are resolved from the repository root."));
    properties.put("xmlContent", stringSchema("Inline XML content as a UTF-8 string."));
    properties.put("inputName", stringSchema("Logical file name for inline XML content."));
    properties.put("persistArtifacts", booleanSchema(
        "Persist input.xml, result.json, and report.xml under target/runs/<run-id>."));
    schema.put("oneOf", List.of(
        Map.of(
            "required", List.of("xmlPath"),
            "not", Map.of("required", List.of("xmlContent"))),
        Map.of(
            "required", List.of("xmlContent"),
            "not", Map.of("required", List.of("xmlPath")))));
    return schema;
  }

  private static Map<String, Object> outputSchemaDefinition() {
    Map<String, Object> schema = objectSchema();
    schema.put("required", List.of(
        "schemaVersion",
        "toolVersion",
        "input",
        "processingSuccessful",
        "scenario",
        "summary",
        "findings",
        "artifacts"));
    Map<String, Object> properties = properties(schema);
    properties.put("schemaVersion", simpleType("string"));
    properties.put("toolVersion", simpleType("string"));
    properties.put("input", objectSchema());
    properties.put("processingSuccessful", simpleType("boolean"));
    properties.put("acceptRecommendation", nullableString());
    properties.put("scenario", objectSchema());
    properties.put("summary", objectSchema());
    properties.put("findings", mapOf("type", "array", "items", objectSchema()));
    properties.put("artifacts", objectSchema());
    properties.put("rawKosit", nullableObject());
    properties.put("toolError", nullableObject());
    return schema;
  }

  private static Map<String, Object> stringSchema(String description) {
    Map<String, Object> schema = simpleType("string");
    schema.put("description", description);
    return schema;
  }

  private static Map<String, Object> booleanSchema(String description) {
    Map<String, Object> schema = simpleType("boolean");
    schema.put("description", description);
    return schema;
  }

  private static Map<String, Object> nullableString() {
    return mapOf("type", List.of("string", "null"));
  }

  private static Map<String, Object> nullableObject() {
    return mapOf("type", List.of("object", "null"));
  }

  private static Map<String, Object> objectSchema() {
    Map<String, Object> schema = mapOf("type", "object");
    schema.put("properties", new LinkedHashMap<String, Object>());
    schema.put("additionalProperties", Boolean.TRUE);
    return schema;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> properties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  private static Map<String, Object> simpleType(String type) {
    return mapOf("type", type);
  }

  private static Map<String, Object> mapOf(Object... values) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      map.put(String.valueOf(values[index]), values[index + 1]);
    }
    return map;
  }
}

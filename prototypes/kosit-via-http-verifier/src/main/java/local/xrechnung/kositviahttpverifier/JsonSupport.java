package local.xrechnung.kositviahttpverifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class JsonSupport {

  private static final TypeReference<Map<String, Object>> MAP_TYPE =
      new TypeReference<Map<String, Object>>() {
      };

  private JsonSupport() {
  }

  static ObjectMapper createObjectMapper() {
    return JsonMapper.builder()
        .findAndAddModules()
        .build();
  }

  static Map<String, Object> toMap(ObjectMapper mapper, Object value) {
    if (value instanceof Map) {
      return new LinkedHashMap<String, Object>((Map<String, Object>) value);
    }
    return mapper.convertValue(value, MAP_TYPE);
  }

  static Map<String, Object> fromJsonMap(ObjectMapper mapper, String value) throws IOException {
    return mapper.readValue(value, MAP_TYPE);
  }

  static Map<String, Object> fromJsonMap(ObjectMapper mapper, byte[] value) throws IOException {
    return mapper.readValue(value, MAP_TYPE);
  }

  static byte[] toPrettyJsonBytes(ObjectMapper mapper, Object value) throws IOException {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
  }

  static String toCompactJson(ObjectMapper mapper, Object value) throws IOException {
    return mapper.writeValueAsString(value);
  }

  static byte[] toJsonBytes(ObjectMapper mapper, Object value) throws IOException {
    return toCompactJson(mapper, value).getBytes(StandardCharsets.UTF_8);
  }

  static String toJsonString(ObjectMapper mapper, Object value) {
    return mapper.writeValueAsString(value);
  }
}

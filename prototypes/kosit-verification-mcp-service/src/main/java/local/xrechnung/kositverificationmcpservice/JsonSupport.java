package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
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
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
  }

  static Map<String, Object> toMap(ObjectMapper mapper, Object value) {
    return mapper.convertValue(value, MAP_TYPE);
  }

  static byte[] toPrettyJsonBytes(ObjectMapper mapper, Object value) throws IOException {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
  }

  static String toJsonString(ObjectMapper mapper, Object value) {
    return mapper.writeValueAsString(value);
  }
}

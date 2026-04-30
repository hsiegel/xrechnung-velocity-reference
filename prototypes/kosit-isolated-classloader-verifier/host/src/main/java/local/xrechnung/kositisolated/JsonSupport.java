package local.xrechnung.kositisolated;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class JsonSupport {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
      .findAndAddModules()
      .build();

  private JsonSupport() {
  }

  static String toJson(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }
}

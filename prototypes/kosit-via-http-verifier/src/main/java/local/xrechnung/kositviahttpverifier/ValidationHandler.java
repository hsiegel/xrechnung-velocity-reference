package local.xrechnung.kositviahttpverifier;

import java.util.Map;

interface ValidationHandler {
  Map<String, Object> validate(Map<String, Object> arguments);
}

package local.xrechnung.kositverificationmcpservice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class ToolFailure extends Exception {

  private final String category;
  private final Map<String, Object> details;

  ToolFailure(String category, String message) {
    this(category, message, Collections.<String, Object>emptyMap(), null);
  }

  ToolFailure(String category, String message, Throwable cause) {
    this(category, message, Collections.<String, Object>emptyMap(), cause);
  }

  ToolFailure(String category, String message, Map<String, Object> details) {
    this(category, message, details, null);
  }

  ToolFailure(String category, String message, Map<String, Object> details, Throwable cause) {
    super(message, cause);
    this.category = category;
    this.details = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details));
  }

  String getCategory() {
    return category;
  }

  Map<String, Object> getDetails() {
    return details;
  }
}

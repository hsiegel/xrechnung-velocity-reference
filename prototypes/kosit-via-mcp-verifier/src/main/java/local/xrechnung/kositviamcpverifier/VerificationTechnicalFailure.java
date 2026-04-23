package local.xrechnung.kositviamcpverifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VerificationTechnicalFailure {

  private final String category;
  private final String message;
  private final Map<String, Object> details;
  private final String stderrTail;

  VerificationTechnicalFailure(
      String category,
      String message,
      Map<String, Object> details,
      String stderrTail) {
    this.category = category;
    this.message = message;
    this.details = details != null
        ? Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details))
        : Collections.<String, Object>emptyMap();
    this.stderrTail = stderrTail;
  }

  static VerificationTechnicalFailure of(
      String category,
      String message,
      Map<String, Object> details,
      String stderrTail) {
    return new VerificationTechnicalFailure(category, message, details, stderrTail);
  }

  public String getCategory() {
    return category;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public String getStderrTail() {
    return stderrTail;
  }
}

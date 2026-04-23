package local.xrechnung.kositviamcpverifier;

import java.util.Map;

public final class VerificationFinding {

  private final String channel;
  private final String severity;
  private final String message;
  private final String location;
  private final String ruleId;
  private final String test;
  private final String flag;
  private final String role;
  private final String rawType;
  private final Integer line;
  private final Integer column;
  private final String stepId;

  VerificationFinding(
      String channel,
      String severity,
      String message,
      String location,
      String ruleId,
      String test,
      String flag,
      String role,
      String rawType,
      Integer line,
      Integer column,
      String stepId) {
    this.channel = channel;
    this.severity = severity;
    this.message = message;
    this.location = location;
    this.ruleId = ruleId;
    this.test = test;
    this.flag = flag;
    this.role = role;
    this.rawType = rawType;
    this.line = line;
    this.column = column;
    this.stepId = stepId;
  }

  static VerificationFinding fromMap(Map<String, Object> finding) {
    return new VerificationFinding(
        text(finding.get("channel")),
        text(finding.get("severity")),
        text(finding.get("message")),
        text(finding.get("location")),
        text(finding.get("ruleId")),
        text(finding.get("test")),
        text(finding.get("flag")),
        text(finding.get("role")),
        text(finding.get("rawType")),
        integer(finding.get("line")),
        integer(finding.get("column")),
        text(finding.get("stepId")));
  }

  public String getChannel() {
    return channel;
  }

  public String getSeverity() {
    return severity;
  }

  public String getMessage() {
    return message;
  }

  public String getLocation() {
    return location;
  }

  public String getRuleId() {
    return ruleId;
  }

  public String getTest() {
    return test;
  }

  public String getFlag() {
    return flag;
  }

  public String getRole() {
    return role;
  }

  public String getRawType() {
    return rawType;
  }

  public Integer getLine() {
    return line;
  }

  public Integer getColumn() {
    return column;
  }

  public String getStepId() {
    return stepId;
  }

  private static String text(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  private static Integer integer(Object value) {
    if (value instanceof Number) {
      return Integer.valueOf(((Number) value).intValue());
    }
    if (value == null) {
      return null;
    }
    return Integer.valueOf(String.valueOf(value));
  }
}

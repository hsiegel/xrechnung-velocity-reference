package local.xrechnung.saxonoutputverifier;

final class VerifierFinding {

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

  VerifierFinding(
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

  String getChannel() {
    return channel;
  }

  String getSeverity() {
    return severity;
  }

  String getMessage() {
    return message;
  }

  String getLocation() {
    return location;
  }

  String getRuleId() {
    return ruleId;
  }

  String getTest() {
    return test;
  }

  String getFlag() {
    return flag;
  }

  String getRole() {
    return role;
  }

  String getRawType() {
    return rawType;
  }

  Integer getLine() {
    return line;
  }

  Integer getColumn() {
    return column;
  }

  String getStepId() {
    return stepId;
  }
}

package local.xrechnung.kositviamcpverifier;

import java.util.Map;

public final class VerificationSummary {

  private final Boolean wellformed;
  private final Boolean schemaValid;
  private final Boolean schematronValid;
  private final Integer processingErrorCount;
  private final Integer xsdFindingCount;
  private final Integer schematronFindingCount;
  private final Integer findingCountTotal;

  VerificationSummary(
      Boolean wellformed,
      Boolean schemaValid,
      Boolean schematronValid,
      Integer processingErrorCount,
      Integer xsdFindingCount,
      Integer schematronFindingCount,
      Integer findingCountTotal) {
    this.wellformed = wellformed;
    this.schemaValid = schemaValid;
    this.schematronValid = schematronValid;
    this.processingErrorCount = processingErrorCount;
    this.xsdFindingCount = xsdFindingCount;
    this.schematronFindingCount = schematronFindingCount;
    this.findingCountTotal = findingCountTotal;
  }

  static VerificationSummary fromMap(Map<String, Object> summary) {
    if (summary == null) {
      return empty();
    }
    return new VerificationSummary(
        bool(summary.get("wellformed")),
        bool(summary.get("schemaValid")),
        bool(summary.get("schematronValid")),
        integer(summary.get("processingErrorCount")),
        integer(summary.get("xsdFindingCount")),
        integer(summary.get("schematronFindingCount")),
        integer(summary.get("findingCountTotal")));
  }

  static VerificationSummary empty() {
    return new VerificationSummary(null, null, null, null, null, null, null);
  }

  public Boolean getWellformed() {
    return wellformed;
  }

  public Boolean getSchemaValid() {
    return schemaValid;
  }

  public Boolean getSchematronValid() {
    return schematronValid;
  }

  public Integer getProcessingErrorCount() {
    return processingErrorCount;
  }

  public Integer getXsdFindingCount() {
    return xsdFindingCount;
  }

  public Integer getSchematronFindingCount() {
    return schematronFindingCount;
  }

  public Integer getFindingCountTotal() {
    return findingCountTotal;
  }

  private static Boolean bool(Object value) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value == null) {
      return null;
    }
    return Boolean.valueOf(String.valueOf(value));
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

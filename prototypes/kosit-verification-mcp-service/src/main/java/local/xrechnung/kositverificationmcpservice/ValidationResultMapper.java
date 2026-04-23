package local.xrechnung.kositverificationmcpservice;

import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.api.XmlError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.oclc.purl.dsdl.svrl.FailedAssert;
import org.oclc.purl.dsdl.svrl.Text;

final class ValidationResultMapper {

  static final String SCHEMA_VERSION = "1.0.0";

  private ValidationResultMapper() {
  }

  static Map<String, Object> successEnvelope(
      String toolVersion,
      ValidationInput input,
      InvoiceScopeInspector.ScopeCheck scopeCheck,
      ReusableValidator.ValidationRun validationRun,
      KositReportParser.ReportAnalysis reportAnalysis,
      Map<String, Object> artifacts) {
    Result result = validationRun.getResult();
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();

    List<String> processingErrors = safeList(result.getProcessingErrors());
    for (String processingError : processingErrors) {
      findings.add(finding(
          "processing",
          "error",
          processingError,
          null,
          null,
          null,
          null,
          null,
          "processing-error",
          null,
          null,
          null));
    }

    List<XmlError> schemaViolations = safeList(result.getSchemaViolations());
    if (!schemaViolations.isEmpty()) {
      for (XmlError schemaViolation : schemaViolations) {
        findings.add(finding(
            "xsd",
            stringify(schemaViolation.getSeverity()),
            trimToNull(schemaViolation.getMessage()),
            lineColumnLocation(schemaViolation.getRowNumber(), schemaViolation.getColumnNumber()),
            null,
            null,
            null,
            null,
            "XmlError",
            schemaViolation.getRowNumber(),
            schemaViolation.getColumnNumber(),
            "val-xsd"));
      }
    } else {
      findings.addAll(filterReportMessages(reportAnalysis.getStepResults(), "xsd"));
    }

    List<FailedAssert> failedAsserts = safeList(result.getFailedAsserts());
    if (!failedAsserts.isEmpty()) {
      for (FailedAssert failedAssert : failedAsserts) {
        findings.add(finding(
            "schematron",
            preferredSeverity(failedAssert.getFlag(), failedAssert.getRole(), "error"),
            flattenText(failedAssert.getText()),
            trimToNull(failedAssert.getLocation()),
            trimToNull(failedAssert.getId()),
            trimToNull(failedAssert.getTest()),
            trimToNull(failedAssert.getFlag()),
            trimToNull(failedAssert.getRole()),
            "FailedAssert",
            null,
            null,
            null));
      }
    } else {
      findings.addAll(filterReportMessages(reportAnalysis.getStepResults(), "schematron"));
    }

    findings.addAll(reportAnalysis.getCustomFindings());

    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("wellformed", Boolean.valueOf(result.isWellformed()));
    summary.put("schemaValid", Boolean.valueOf(result.isSchemaValid()));
    summary.put("schematronValid", Boolean.valueOf(result.isSchematronValid()));
    summary.put("processingErrorCount", Integer.valueOf(processingErrors.size()));
    summary.put("xsdFindingCount", Integer.valueOf(countFindings(findings, "xsd")));
    summary.put("schematronFindingCount", Integer.valueOf(countFindings(findings, "schematron")));
    summary.put("findingCountTotal", Integer.valueOf(findings.size()));

    Map<String, Object> scenario = new LinkedHashMap<String, Object>();
    scenario.put("matched", Boolean.valueOf(reportAnalysis.isScenarioMatched()));
    scenario.put("name", reportAnalysis.getScenarioName());
    scenario.put("customizationId", scopeCheck.getCustomizationId());

    Map<String, Object> rawKosit = new LinkedHashMap<String, Object>();
    rawKosit.put("acceptable", Boolean.valueOf(result.isAcceptable()));
    rawKosit.put("wellformed", Boolean.valueOf(result.isWellformed()));
    rawKosit.put("schemaValid", Boolean.valueOf(result.isSchemaValid()));
    rawKosit.put("schematronValid", Boolean.valueOf(result.isSchematronValid()));
    rawKosit.put("acceptRecommendation", stringify(result.getAcceptRecommendation()));
    rawKosit.put("processingErrors", new ArrayList<String>(processingErrors));
    rawKosit.put("schemaViolations", rawSchemaViolations(schemaViolations));
    rawKosit.put("failedAsserts", rawFailedAsserts(failedAsserts));
    rawKosit.put("report", reportAnalysis.getReport());

    Map<String, Object> envelope = new LinkedHashMap<String, Object>();
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("toolVersion", toolVersion);
    envelope.put("input", input.toInputBlock());
    envelope.put("processingSuccessful", Boolean.valueOf(result.isProcessingSuccessful()));
    envelope.put("acceptRecommendation", stringify(result.getAcceptRecommendation()));
    envelope.put("scenario", scenario);
    envelope.put("summary", summary);
    envelope.put("findings", findings);
    envelope.put("artifacts", artifacts);
    envelope.put("rawKosit", rawKosit);
    envelope.put("toolError", null);
    return envelope;
  }

  static Map<String, Object> errorEnvelope(
      String toolVersion,
      Map<String, Object> inputBlock,
      ToolFailure failure,
      Map<String, Object> artifacts) {
    Map<String, Object> scenario = new LinkedHashMap<String, Object>();
    scenario.put("matched", Boolean.FALSE);
    scenario.put("name", null);
    scenario.put("customizationId", failure.getDetails().get("customizationId"));

    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("wellformed", null);
    summary.put("schemaValid", null);
    summary.put("schematronValid", null);
    summary.put("processingErrorCount", Integer.valueOf(0));
    summary.put("xsdFindingCount", Integer.valueOf(0));
    summary.put("schematronFindingCount", Integer.valueOf(0));
    summary.put("findingCountTotal", Integer.valueOf(0));

    Map<String, Object> toolError = new LinkedHashMap<String, Object>();
    toolError.put("category", failure.getCategory());
    toolError.put("message", failure.getMessage());
    toolError.put("details", failure.getDetails().isEmpty() ? null : failure.getDetails());

    Map<String, Object> envelope = new LinkedHashMap<String, Object>();
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("toolVersion", toolVersion);
    envelope.put("input", inputBlock);
    envelope.put("processingSuccessful", Boolean.FALSE);
    envelope.put("acceptRecommendation", null);
    envelope.put("scenario", scenario);
    envelope.put("summary", summary);
    envelope.put("findings", Collections.emptyList());
    envelope.put("artifacts", artifacts);
    envelope.put("rawKosit", null);
    envelope.put("toolError", toolError);
    return envelope;
  }

  private static List<Map<String, Object>> filterReportMessages(
      List<Map<String, Object>> stepResults,
      String channel) {
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> step : stepResults) {
      if (!channel.equals(step.get("channel"))) {
        continue;
      }
      Object messages = step.get("messages");
      if (messages instanceof List) {
        for (Object message : (List<?>) messages) {
          if (message instanceof Map) {
            findings.add(new LinkedHashMap<String, Object>((Map<String, Object>) message));
          }
        }
      }
    }
    return findings;
  }

  private static List<Map<String, Object>> rawSchemaViolations(List<XmlError> schemaViolations) {
    List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
    for (XmlError schemaViolation : schemaViolations) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("severity", stringify(schemaViolation.getSeverity()));
      entry.put("message", trimToNull(schemaViolation.getMessage()));
      entry.put("line", schemaViolation.getRowNumber());
      entry.put("column", schemaViolation.getColumnNumber());
      raw.add(entry);
    }
    return raw;
  }

  private static List<Map<String, Object>> rawFailedAsserts(List<FailedAssert> failedAsserts) {
    List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
    for (FailedAssert failedAssert : failedAsserts) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("id", trimToNull(failedAssert.getId()));
      entry.put("flag", trimToNull(failedAssert.getFlag()));
      entry.put("role", trimToNull(failedAssert.getRole()));
      entry.put("test", trimToNull(failedAssert.getTest()));
      entry.put("location", trimToNull(failedAssert.getLocation()));
      entry.put("text", flattenText(failedAssert.getText()));
      raw.add(entry);
    }
    return raw;
  }

  private static int countFindings(List<Map<String, Object>> findings, String channel) {
    int count = 0;
    for (Map<String, Object> finding : findings) {
      if (channel.equals(finding.get("channel"))) {
        count++;
      }
    }
    return count;
  }

  private static <T> List<T> safeList(List<T> values) {
    return values != null ? values : Collections.<T>emptyList();
  }

  private static Map<String, Object> finding(
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
    Map<String, Object> finding = new LinkedHashMap<String, Object>();
    finding.put("channel", channel);
    finding.put("severity", severity);
    finding.put("message", message);
    finding.put("location", location);
    finding.put("ruleId", ruleId);
    finding.put("test", test);
    finding.put("flag", flag);
    finding.put("role", role);
    finding.put("rawType", rawType);
    finding.put("line", line);
    finding.put("column", column);
    finding.put("stepId", stepId);
    return finding;
  }

  private static String preferredSeverity(String flag, String role, String fallback) {
    if (trimToNull(flag) != null) {
      return trimToNull(flag);
    }
    if (trimToNull(role) != null) {
      return trimToNull(role);
    }
    return fallback;
  }

  private static String lineColumnLocation(Integer line, Integer column) {
    if (line == null) {
      return null;
    }
    return column != null ? "line " + line + ", column " + column : "line " + line;
  }

  private static String flattenText(Text text) {
    if (text == null || text.getContent() == null || text.getContent().isEmpty()) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (Object part : text.getContent()) {
      String chunk = String.valueOf(part).trim();
      if (!chunk.isEmpty()) {
        if (builder.length() > 0) {
          builder.append(' ');
        }
        builder.append(chunk);
      }
    }
    return trimToNull(builder.toString());
  }

  private static String stringify(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}

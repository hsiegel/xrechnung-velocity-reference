package local.xrechnung.kositviamcpverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class VerificationResultMapper {

  private final ObjectMapper objectMapper;
  private final boolean includeRawServiceResult;

  VerificationResultMapper(ObjectMapper objectMapper, boolean includeRawServiceResult) {
    this.objectMapper = objectMapper;
    this.includeRawServiceResult = includeRawServiceResult;
  }

  VerificationResult fromStructuredContent(Map<String, Object> serviceResult) {
    boolean processingSuccessful = bool(serviceResult.get("processingSuccessful"));
    String acceptRecommendation = text(serviceResult.get("acceptRecommendation"));
    VerificationTechnicalFailure technicalFailure = toolError(serviceResult.get("toolError"));
    return new VerificationResult(
        processingSuccessful,
        isAccepted(acceptRecommendation),
        acceptRecommendation,
        map(serviceResult.get("scenario")),
        VerificationSummary.fromMap(map(serviceResult.get("summary"))),
        findings(serviceResult.get("findings")),
        map(serviceResult.get("artifacts")),
        includeRawServiceResult ? JsonSupport.toMap(objectMapper, serviceResult) : null,
        technicalFailure);
  }

  private VerificationTechnicalFailure toolError(Object value) {
    Map<String, Object> toolError = map(value);
    if (toolError == null || toolError.isEmpty()) {
      return null;
    }
    return VerificationTechnicalFailure.of(
        text(toolError.get("category")),
        text(toolError.get("message")),
        map(toolError.get("details")),
        null);
  }

  private List<VerificationFinding> findings(Object value) {
    if (!(value instanceof List)) {
      return Collections.emptyList();
    }
    List<VerificationFinding> findings = new ArrayList<VerificationFinding>();
    for (Object entry : (List<?>) value) {
      Map<String, Object> finding = map(entry);
      if (finding != null) {
        findings.add(VerificationFinding.fromMap(finding));
      }
    }
    return findings;
  }

  private Map<String, Object> map(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map) {
      return new LinkedHashMap<String, Object>((Map<String, Object>) value);
    }
    return JsonSupport.toMap(objectMapper, value);
  }

  private static boolean isAccepted(String acceptRecommendation) {
    return acceptRecommendation != null
        && acceptRecommendation.toUpperCase().startsWith("ACCEPT");
  }

  private static boolean bool(Object value) {
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    if (value == null) {
      return false;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static String text(Object value) {
    return value != null ? String.valueOf(value) : null;
  }
}

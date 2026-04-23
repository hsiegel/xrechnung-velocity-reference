package local.xrechnung.kositviamcpverifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VerificationResult {

  private final boolean processingSuccessful;
  private final boolean accepted;
  private final String acceptRecommendation;
  private final Map<String, Object> scenario;
  private final VerificationSummary summary;
  private final List<VerificationFinding> findings;
  private final Map<String, Object> artifacts;
  private final Map<String, Object> rawServiceResult;
  private final VerificationTechnicalFailure technicalFailure;

  VerificationResult(
      boolean processingSuccessful,
      boolean accepted,
      String acceptRecommendation,
      Map<String, Object> scenario,
      VerificationSummary summary,
      List<VerificationFinding> findings,
      Map<String, Object> artifacts,
      Map<String, Object> rawServiceResult,
      VerificationTechnicalFailure technicalFailure) {
    this.processingSuccessful = processingSuccessful;
    this.accepted = accepted;
    this.acceptRecommendation = acceptRecommendation;
    this.scenario = immutableMap(scenario);
    this.summary = summary;
    this.findings = findings != null
        ? Collections.unmodifiableList(findings)
        : Collections.<VerificationFinding>emptyList();
    this.artifacts = immutableMap(artifacts);
    this.rawServiceResult = rawServiceResult != null ? immutableMap(rawServiceResult) : null;
    this.technicalFailure = technicalFailure;
  }

  static VerificationResult technicalFailure(VerificationTechnicalFailure failure) {
    return new VerificationResult(
        false,
        false,
        null,
        Collections.<String, Object>emptyMap(),
        VerificationSummary.empty(),
        Collections.<VerificationFinding>emptyList(),
        Collections.<String, Object>emptyMap(),
        null,
        failure);
  }

  public boolean isProcessingSuccessful() {
    return processingSuccessful;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getAcceptRecommendation() {
    return acceptRecommendation;
  }

  public Map<String, Object> getScenario() {
    return scenario;
  }

  public VerificationSummary getSummary() {
    return summary;
  }

  public List<VerificationFinding> getFindings() {
    return findings;
  }

  public Map<String, Object> getArtifacts() {
    return artifacts;
  }

  public Map<String, Object> getRawServiceResult() {
    return rawServiceResult;
  }

  public VerificationTechnicalFailure getTechnicalFailure() {
    return technicalFailure;
  }

  boolean isTechnicalFailure() {
    return technicalFailure != null || !processingSuccessful;
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(value));
  }
}

package local.xrechnung.saxonoutputverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ProfileGate {

  private ProfileGate() {
  }

  static Result evaluate(
      InvoiceInspector.DocumentInfo documentInfo,
      ExpectedProfile expectedProfile,
      boolean scenarioMatch) {
    List<Mismatch> mismatches = new ArrayList<Mismatch>();

    if (!expectedProfile.getRootLocalName().equals(documentInfo.getRootLocalName())
        || !expectedProfile.getRootNamespaceUri().equals(documentInfo.getRootNamespaceUri())) {
      mismatches.add(new Mismatch(
          "root_mismatch",
          "Root element does not match the expected UBL Invoice QName."));
    }

    if (!sameValue(expectedProfile.getCustomizationId(), documentInfo.getCustomizationId())) {
      mismatches.add(new Mismatch(
          "customization_id_mismatch",
          "CustomizationID does not match the expected XRechnung profile."));
    }

    if (!sameValue(expectedProfile.getProfileId(), documentInfo.getProfileId())) {
      mismatches.add(new Mismatch(
          "profile_id_mismatch",
          "ProfileID does not match the expected billing profile."));
    }

    if (!scenarioMatch) {
      mismatches.add(new Mismatch(
          "scenario_match_mismatch",
          "The bundle match XPath for the expected scenario did not match the input."));
    }

    return new Result(mismatches.isEmpty(), mismatches);
  }

  private static boolean sameValue(String expected, String actual) {
    if (expected == null) {
      return actual == null;
    }
    return expected.equals(actual);
  }

  static final class Result {
    private final boolean matches;
    private final List<Mismatch> mismatches;

    Result(boolean matches, List<Mismatch> mismatches) {
      this.matches = matches;
      this.mismatches = Collections.unmodifiableList(new ArrayList<Mismatch>(mismatches));
    }

    boolean matches() {
      return matches;
    }

    List<Mismatch> getMismatches() {
      return mismatches;
    }
  }

  static final class Mismatch {
    private final String code;
    private final String message;

    Mismatch(String code, String message) {
      this.code = code;
      this.message = message;
    }

    String getCode() {
      return code;
    }

    String getMessage() {
      return message;
    }
  }
}

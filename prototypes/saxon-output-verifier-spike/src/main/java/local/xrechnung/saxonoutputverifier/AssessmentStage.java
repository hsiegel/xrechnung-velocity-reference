package local.xrechnung.saxonoutputverifier;

import java.nio.file.Path;

final class AssessmentStage {

  private AssessmentStage() {
  }

  static Status describe(Path configDirectory, ScenarioDefinition scenario) {
    Path reportXsl = configDirectory.resolve(scenario.getReportLocation()).normalize();
    Path defaultReportXsl = configDirectory.resolve("resources/default-report.xsl").normalize();
    String note =
        "Not implemented in this spike. The bundle report layer expects a KoSIT-style "
            + "in:createReportInput document, applies "
            + scenario.getCustomLevels().size()
            + " customLevel override(s), and only then derives accept/reject.";
    return new Status(reportXsl, defaultReportXsl, note);
  }

  static final class Status {
    private final Path reportXsl;
    private final Path defaultReportXsl;
    private final String note;

    Status(Path reportXsl, Path defaultReportXsl, String note) {
      this.reportXsl = reportXsl;
      this.defaultReportXsl = defaultReportXsl;
      this.note = note;
    }

    Path getReportXsl() {
      return reportXsl;
    }

    Path getDefaultReportXsl() {
      return defaultReportXsl;
    }

    String getNote() {
      return note;
    }
  }
}

package local.xrechnung.kositverificationmcpservice;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tools.jackson.databind.ObjectMapper;

final class RunArtifactStore {

  private static final String INPUT_XML = "input.xml";
  private static final String REPORT_XML = "report.xml";
  private static final String RESULT_JSON = "result.json";
  private static final String RESOURCE_SCHEME = "xrechnung-run";

  private final Path runsRoot;
  private final ObjectMapper objectMapper;
  private final ConcurrentMap<String, PersistedRun> runs = new ConcurrentHashMap<String, PersistedRun>();

  RunArtifactStore(Path runsRoot, ObjectMapper objectMapper) {
    this.runsRoot = runsRoot;
    this.objectMapper = objectMapper;
  }

  RunHandle begin(ValidationInput input) throws ToolFailure {
    if (!input.isPersistArtifacts()) {
      return RunHandle.notPersisted();
    }

    String runId = "run-" + UUID.randomUUID().toString();
    Path runDirectory = runsRoot.resolve(runId);
    try {
      Files.createDirectories(runDirectory);
      Path inputPath = runDirectory.resolve(INPUT_XML);
      Files.write(inputPath, input.getXmlBytes());
      RunHandle handle = new RunHandle(runId, runDirectory, inputPath, null, null);
      runs.put(runId, new PersistedRun(runDirectory, handle.artifactPaths()));
      return handle;
    } catch (IOException e) {
      throw new ToolFailure(
          "artifact_write_failed",
          "Could not create run artifacts under " + runDirectory,
          Map.of("runId", runId, "runDirectory", runDirectory.toString()),
          e);
    }
  }

  RunHandle storeReport(RunHandle handle, byte[] reportXmlBytes) throws ToolFailure {
    if (!handle.isPersisted() || reportXmlBytes == null) {
      return handle;
    }
    try {
      Path reportPath = handle.getRunDirectory().resolve(REPORT_XML);
      Files.write(reportPath, reportXmlBytes);
      RunHandle updated = handle.withReportPath(reportPath);
      runs.put(updated.getRunId(), new PersistedRun(updated.getRunDirectory(), updated.artifactPaths()));
      return updated;
    } catch (IOException e) {
      throw new ToolFailure(
          "artifact_write_failed",
          "Could not write report.xml for run " + handle.getRunId(),
          Map.of("runId", handle.getRunId()),
          e);
    }
  }

  RunHandle writeResult(RunHandle handle, Map<String, Object> result) throws ToolFailure {
    if (!handle.isPersisted()) {
      return handle;
    }
    try {
      Path resultPath = handle.getRunDirectory().resolve(RESULT_JSON);
      Files.write(resultPath, JsonSupport.toPrettyJsonBytes(objectMapper, result));
      RunHandle updated = handle.withResultPath(resultPath);
      runs.put(updated.getRunId(), new PersistedRun(updated.getRunDirectory(), updated.artifactPaths()));
      return updated;
    } catch (IOException e) {
      throw new ToolFailure(
          "artifact_write_failed",
          "Could not write result.json for run " + handle.getRunId(),
          Map.of("runId", handle.getRunId()),
          e);
    }
  }

  Map<String, Object> artifactBlock(RunHandle handle, boolean reportAvailable) {
    Map<String, Object> block = new LinkedHashMap<String, Object>();
    block.put("persisted", Boolean.valueOf(handle.isPersisted()));
    block.put("runId", handle.getRunId());
    block.put("runDirectory", handle.getRunDirectory() != null ? handle.getRunDirectory().toString() : null);
    block.put("inputXmlPath", handle.getInputPath() != null ? handle.getInputPath().toString() : null);
    block.put("resultJsonPath", handle.getResultPath() != null ? handle.getResultPath().toString() : null);
    block.put("reportXmlPath", handle.getReportPath() != null ? handle.getReportPath().toString() : null);
    block.put("reportXmlAvailable", Boolean.valueOf(reportAvailable));
    block.put("resources", handle.resourceUris());
    return block;
  }

  ReadableArtifact readArtifact(String uriValue) throws ToolFailure {
    URI uri = URI.create(uriValue);
    if (!RESOURCE_SCHEME.equals(uri.getScheme())) {
      throw new ToolFailure("input_invalid", "Unsupported resource URI: " + uriValue);
    }
    String runId = uri.getHost();
    String artifactName = uri.getPath();
    if (artifactName != null && artifactName.startsWith("/")) {
      artifactName = artifactName.substring(1);
    }
    if (runId == null || artifactName == null || artifactName.isEmpty()) {
      throw new ToolFailure("input_invalid", "Malformed resource URI: " + uriValue);
    }

    PersistedRun run = runs.get(runId);
    if (run == null) {
      throw new ToolFailure(
          "input_unreadable",
          "Unknown run id: " + runId,
          Map.of("runId", runId));
    }
    Path artifactPath = run.artifactPaths().get(artifactName);
    if (artifactPath == null || !Files.isRegularFile(artifactPath)) {
      throw new ToolFailure(
          "input_unreadable",
          "Artifact not found for run " + runId + ": " + artifactName,
          Map.of("runId", runId, "artifact", artifactName));
    }

    try {
      return new ReadableArtifact(
          uriValue,
          mimeType(artifactName),
          Files.readString(artifactPath),
          artifactPath);
    } catch (IOException e) {
      throw new ToolFailure(
          "input_unreadable",
          "Could not read artifact for run " + runId + ": " + artifactName,
          Map.of("runId", runId, "artifact", artifactName),
          e);
    }
  }

  private static String mimeType(String artifactName) {
    if (RESULT_JSON.equals(artifactName)) {
      return "application/json";
    }
    return "application/xml";
  }

  static final class RunHandle {
    private final String runId;
    private final Path runDirectory;
    private final Path inputPath;
    private final Path reportPath;
    private final Path resultPath;

    private RunHandle(String runId, Path runDirectory, Path inputPath, Path reportPath, Path resultPath) {
      this.runId = runId;
      this.runDirectory = runDirectory;
      this.inputPath = inputPath;
      this.reportPath = reportPath;
      this.resultPath = resultPath;
    }

    static RunHandle notPersisted() {
      return new RunHandle(null, null, null, null, null);
    }

    boolean isPersisted() {
      return runId != null;
    }

    String getRunId() {
      return runId;
    }

    Path getRunDirectory() {
      return runDirectory;
    }

    Path getInputPath() {
      return inputPath;
    }

    Path getReportPath() {
      return reportPath;
    }

    Path getResultPath() {
      return resultPath;
    }

    RunHandle withReportPath(Path value) {
      return new RunHandle(runId, runDirectory, inputPath, value, resultPath);
    }

    RunHandle withResultPath(Path value) {
      return new RunHandle(runId, runDirectory, inputPath, reportPath, value);
    }

    Map<String, Path> artifactPaths() {
      if (!isPersisted()) {
        return Collections.emptyMap();
      }
      Map<String, Path> paths = new LinkedHashMap<String, Path>();
      paths.put(INPUT_XML, inputPath);
      if (reportPath != null) {
        paths.put(REPORT_XML, reportPath);
      }
      if (resultPath != null) {
        paths.put(RESULT_JSON, resultPath);
      }
      return paths;
    }

    Map<String, Object> resourceUris() {
      if (!isPersisted()) {
        return Collections.emptyMap();
      }
      Map<String, Object> resources = new LinkedHashMap<String, Object>();
      resources.put("inputXml", resourceUri(runId, INPUT_XML));
      resources.put("resultJson", resultPath != null ? resourceUri(runId, RESULT_JSON) : null);
      resources.put("reportXml", reportPath != null ? resourceUri(runId, REPORT_XML) : null);
      return resources;
    }

    private static String resourceUri(String runId, String artifactName) {
      return RESOURCE_SCHEME + "://" + runId + "/" + artifactName;
    }
  }

  static final class ReadableArtifact {
    private final String uri;
    private final String mimeType;
    private final String text;
    private final Path path;

    ReadableArtifact(String uri, String mimeType, String text, Path path) {
      this.uri = uri;
      this.mimeType = mimeType;
      this.text = text;
      this.path = path;
    }

    String getUri() {
      return uri;
    }

    String getMimeType() {
      return mimeType;
    }

    String getText() {
      return text;
    }

    Path getPath() {
      return path;
    }
  }

  private static final class PersistedRun {
    private final Path runDirectory;
    private final Map<String, Path> artifactPaths;

    private PersistedRun(Path runDirectory, Map<String, Path> artifactPaths) {
      this.runDirectory = runDirectory;
      this.artifactPaths = artifactPaths;
    }

    Path runDirectory() {
      return runDirectory;
    }

    Map<String, Path> artifactPaths() {
      return artifactPaths;
    }
  }
}

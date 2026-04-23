package local.xrechnung.kositviamcpverifier;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import java.nio.file.Path;

final class WorkingDirectoryStdioClientTransport extends StdioClientTransport {

  private final Path workingDirectory;

  WorkingDirectoryStdioClientTransport(
      ServerParameters params,
      McpJsonMapper jsonMapper,
      Path workingDirectory) {
    super(params, jsonMapper);
    this.workingDirectory = workingDirectory;
  }

  @Override
  protected ProcessBuilder getProcessBuilder() {
    ProcessBuilder builder = super.getProcessBuilder();
    if (workingDirectory != null) {
      builder.directory(workingDirectory.toFile());
    }
    return builder;
  }
}

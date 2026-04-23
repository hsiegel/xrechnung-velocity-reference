package local.xrechnung.kositviamcpverifier;

import io.modelcontextprotocol.client.transport.ServerParameters;
import java.nio.file.Path;
import java.util.List;

final class McpServiceProcess {

  private final String command;
  private final List<String> args;
  private final Path workingDirectory;

  private McpServiceProcess(String command, List<String> args, Path workingDirectory) {
    this.command = command;
    this.args = List.copyOf(args);
    this.workingDirectory = workingDirectory;
  }

  static McpServiceProcess forServiceJar(Path serviceJar, Path workingDirectory) {
    return new McpServiceProcess(
        "java",
        List.of("-jar", serviceJar.toAbsolutePath().normalize().toString()),
        workingDirectory);
  }

  ServerParameters toServerParameters() {
    return ServerParameters.builder(command)
        .args(args)
        .build();
  }

  Path getWorkingDirectory() {
    return workingDirectory;
  }
}

package local.xrechnung.kositviamcpverifier;

import java.util.ArrayDeque;
import java.util.Deque;

final class StderrCapture {

  private final int maxLines;
  private final Deque<String> lines = new ArrayDeque<String>();

  StderrCapture(int maxLines) {
    this.maxLines = maxLines;
  }

  synchronized void accept(String line) {
    if (line == null || line.isBlank()) {
      return;
    }
    if (lines.size() == maxLines) {
      lines.removeFirst();
    }
    lines.addLast(line);
    System.err.println("[kosit-verification-mcp-service] " + line);
  }

  synchronized String tail() {
    if (lines.isEmpty()) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (String line : lines) {
      if (builder.length() > 0) {
        builder.append(System.lineSeparator());
      }
      builder.append(line);
    }
    return builder.toString();
  }
}

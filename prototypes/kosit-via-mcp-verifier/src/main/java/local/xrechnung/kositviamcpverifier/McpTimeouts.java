package local.xrechnung.kositviamcpverifier;

import java.time.Duration;

final class McpTimeouts {

  private final Duration initializationTimeout;
  private final Duration requestTimeout;

  private McpTimeouts(Duration initializationTimeout, Duration requestTimeout) {
    this.initializationTimeout = initializationTimeout;
    this.requestTimeout = requestTimeout;
  }

  static McpTimeouts singleValue(long timeoutMillis) {
    Duration timeout = Duration.ofMillis(timeoutMillis);
    return new McpTimeouts(timeout, timeout);
  }

  Duration getInitializationTimeout() {
    return initializationTimeout;
  }

  Duration getRequestTimeout() {
    return requestTimeout;
  }
}

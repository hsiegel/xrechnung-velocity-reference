package local.xrechnung.kositviahttpverifier;

import java.time.Duration;

final class HttpTimeouts {

  private final Duration initializationTimeout;
  private final Duration validationTimeout;

  private HttpTimeouts(Duration initializationTimeout, Duration validationTimeout) {
    this.initializationTimeout = initializationTimeout;
    this.validationTimeout = validationTimeout;
  }

  static HttpTimeouts singleValue(long timeoutMillis) {
    Duration timeout = Duration.ofMillis(timeoutMillis);
    return new HttpTimeouts(timeout, timeout);
  }

  static HttpTimeouts of(long initializationTimeoutMillis, long validationTimeoutMillis) {
    return new HttpTimeouts(
        Duration.ofMillis(initializationTimeoutMillis),
        Duration.ofMillis(validationTimeoutMillis));
  }

  Duration getInitializationTimeout() {
    return initializationTimeout;
  }

  Duration getValidationTimeout() {
    return validationTimeout;
  }
}

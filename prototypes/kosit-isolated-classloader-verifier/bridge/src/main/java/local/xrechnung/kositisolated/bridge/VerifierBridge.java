package local.xrechnung.kositisolated.bridge;

import java.util.Map;

/**
 * Tiny boundary between the host class path and the isolated KoSIT runtime.
 */
public interface VerifierBridge extends AutoCloseable {

  Map<String, Object> verify(Map<String, String> request) throws Exception;

  Map<String, Object> diagnostics(Map<String, String> request) throws Exception;

  @Override
  default void close() throws Exception {
  }
}

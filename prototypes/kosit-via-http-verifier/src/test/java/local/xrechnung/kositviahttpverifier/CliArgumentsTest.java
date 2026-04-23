package local.xrechnung.kositviahttpverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.Map;
import org.junit.Test;

public class CliArgumentsTest {

  @Test
  public void parsesXmlPathRequest() throws Exception {
    CliArguments arguments = CliArguments.parse(new String[] {
        "--xml", "invoice.xml",
        "--persist-artifacts", "true",
        "--timeout-ms", "42",
        "--validation-timeout-ms", "300000",
        "--print-raw", "true"
    });

    Map<String, Object> toolArguments = arguments.toVerificationRequest().toToolArguments();
    assertEquals("invoice.xml", toolArguments.get("xmlPath"));
    assertEquals(Boolean.TRUE, toolArguments.get("persistArtifacts"));
    assertEquals(Duration.ofMillis(42), arguments.toTimeouts().getInitializationTimeout());
    assertEquals(Duration.ofMillis(300000), arguments.toTimeouts().getValidationTimeout());
    assertTrue(arguments.isPrintRaw());
  }

  @Test
  public void usesGenerousValidationTimeoutByDefault() throws Exception {
    CliArguments arguments = CliArguments.parse(new String[] {
        "--xml", "invoice.xml"
    });

    assertEquals(
        Duration.ofMillis(CliArguments.DEFAULT_VALIDATION_TIMEOUT_MILLIS),
        arguments.toTimeouts().getValidationTimeout());
  }

  @Test
  public void rejectsBothXmlPathAndXmlContent() throws Exception {
    try {
      CliArguments.parse(new String[] {
          "--xml", "invoice.xml",
          "--xml-content", "<Invoice/>"
      });
      fail("Expected CliUsageException");
    } catch (CliUsageException e) {
      assertTrue(e.getMessage().contains("Exactly one of --xml or --xml-content"));
    }
  }

  @Test
  public void rejectsUnknownArgument() throws Exception {
    try {
      CliArguments.parse(new String[] {
          "--xml", "invoice.xml",
          "--wat", "nope"
      });
      fail("Expected CliUsageException");
    } catch (CliUsageException e) {
      assertTrue(e.getMessage().contains("Unexpected argument"));
    }
  }
}

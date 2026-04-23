package local.xrechnung.kositviamcpverifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CliArgumentsTest {

  @Test
  public void parsesMinimalXmlPathRequest() throws Exception {
    Path serviceJar = Files.createTempFile("kosit-service", ".jar");

    CliArguments arguments = CliArguments.parse(new String[] {
        "--service-jar", serviceJar.toString(),
        "--xml", "examples/invoice.xml"
    });

    VerificationRequest request = arguments.toVerificationRequest();
    assertTrue(request.toToolArguments().containsKey("xmlPath"));
    assertFalse(request.isPersistArtifacts());
  }

  @Test
  public void rejectsMissingServiceJar() throws Exception {
    try {
      CliArguments.parse(new String[] {
          "--service-jar", "/definitely/missing/kosit-service.jar",
          "--xml", "examples/invoice.xml"
      });
    } catch (CliUsageException e) {
      assertTrue(e.getMessage().contains("Service JAR not found"));
      return;
    }
    throw new AssertionError("Expected CliUsageException");
  }

  @Test
  public void rejectsBothInputForms() throws Exception {
    Path serviceJar = Files.createTempFile("kosit-service", ".jar");

    try {
      CliArguments.parse(new String[] {
          "--service-jar", serviceJar.toString(),
          "--xml", "examples/invoice.xml",
          "--xml-content", "<Invoice/>"
      });
    } catch (CliUsageException e) {
      assertTrue(e.getMessage().contains("Exactly one"));
      return;
    }
    throw new AssertionError("Expected CliUsageException");
  }
}

package local.xrechnung.kositembeddedverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CliArgumentsTest {

  @Test
  public void parseAcceptsRequiredAndOptionalPaths() throws IOException {
    Path xmlFile = Files.createTempFile("kosit-embedded-verifier-", ".xml");
    CliArguments arguments = CliArguments.parse(new String[] {
        "--xml", xmlFile.toString(),
        "--report-dir", "reports",
        "--work-dir", "work"
    });

    Path resolvedXmlFile = arguments.requireXmlPath();
    assertEquals(xmlFile.toAbsolutePath().normalize(), resolvedXmlFile);
    assertTrue(arguments.resolveReportDir(resolvedXmlFile).toString().endsWith("reports"));
    assertTrue(arguments.resolveWorkDir(Path.of("/tmp/project")).toString().endsWith("work"));

    Files.deleteIfExists(xmlFile);
  }

  @Test
  public void parseAllowsHelpWithoutXml() {
    CliArguments arguments = CliArguments.parse(new String[] {"--help"});

    assertTrue(arguments.isHelp());
  }

  @Test
  public void parseRejectsUnknownArgument() {
    try {
      CliArguments.parse(new String[] {"--xml", "invoice.xml", "--wat"});
      fail("Expected parse to fail.");
    } catch (IllegalArgumentException expected) {
      assertEquals("Unknown argument: --wat", expected.getMessage());
    }
  }
}

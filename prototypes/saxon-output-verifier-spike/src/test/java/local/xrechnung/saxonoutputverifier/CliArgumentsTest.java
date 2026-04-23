package local.xrechnung.saxonoutputverifier;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CliArgumentsTest {

  @Test
  public void usesDefaultProfileAndDefaultResultPath() throws Exception {
    Path xmlFile = Files.createTempFile("saxon-cli-", ".xml");
    Files.write(xmlFile, "<Invoice/>".getBytes("UTF-8"));

    CliArguments arguments = CliArguments.parse(new String[] {"--xml", xmlFile.toString()});
    Path projectRoot = Files.createTempDirectory("saxon-project-");
    Path workDir = arguments.resolveWorkDir(projectRoot);
    Path resultJson = arguments.resolveResultJsonPath(workDir, arguments.requireXmlPath());

    assertEquals(ProfileRegistry.DEFAULT_PROFILE_ID, arguments.getProfileId());
    assertEquals(
        projectRoot.resolve("prototypes/saxon-output-verifier-spike/target/validator-work")
            .toAbsolutePath().normalize(),
        workDir);
    assertEquals(
        workDir.resolve("results").resolve(stripExtension(xmlFile.getFileName().toString()) + "-result.json")
            .toAbsolutePath().normalize(),
        resultJson);
  }

  @Test
  public void usesExplicitProfileAndExplicitResultPath() throws Exception {
    Path xmlFile = Files.createTempFile("saxon-cli-", ".xml");
    Files.write(xmlFile, "<Invoice/>".getBytes("UTF-8"));
    Path resultJson = Files.createTempDirectory("saxon-results-").resolve("custom.json");

    CliArguments arguments = CliArguments.parse(new String[] {
        "--xml", xmlFile.toString(),
        "--profile", "xrechnung-ubl-invoice",
        "--result-json", resultJson.toString()
    });

    assertEquals("xrechnung-ubl-invoice", arguments.getProfileId());
    assertEquals(resultJson.toAbsolutePath().normalize(),
        arguments.resolveResultJsonPath(Files.createTempDirectory("unused-"), arguments.requireXmlPath()));
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }
}

package local.xrechnung.kositembeddedverifier;

import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.input.ByteArrayInput;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sf.saxon.s9api.Processor;

final class EmbeddedValidator {

  private EmbeddedValidator() {
  }

  static ValidationRun validate(
      byte[] xmlBytes,
      Path xmlPath,
      Path reportDir,
      ValidatorConfigSupport.PreparedConfiguration configuration) throws Exception {
    Files.createDirectories(reportDir);

    Processor processor = ProcessorProvider.getProcessor();
    URI repositoryUri = asDirectoryUri(configuration.getConfigDirectory());
    Configuration validatorConfiguration =
        Configuration.load(configuration.getScenariosFile().toUri(), repositoryUri)
            .build(processor);
    DefaultCheck check = new DefaultCheck(processor, validatorConfiguration);
    Result result = check.checkInput(new ByteArrayInput(xmlBytes, xmlPath.toUri().toString(), "SHA-256"));

    Path reportPath = reportDir.resolve(stripExtension(xmlPath.getFileName().toString()) + "-report.xml");
    if (result.getReportDocument() != null) {
      XmlReportWriter.write(result.getReportDocument(), reportPath);
    } else {
      reportPath = null;
    }

    return new ValidationRun(result, reportPath);
  }

  private static URI asDirectoryUri(Path directory) {
    String value = directory.toAbsolutePath().toUri().toString();
    return URI.create(value.endsWith("/") ? value : value + "/");
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  static final class ValidationRun {
    private final Result result;
    private final Path reportPath;

    ValidationRun(Result result, Path reportPath) {
      this.result = result;
      this.reportPath = reportPath;
    }

    Result getResult() {
      return result;
    }

    Path getReportPath() {
      return reportPath;
    }

    boolean isAccepted() {
      return result.isProcessingSuccessful() && result.isAcceptable();
    }
  }
}

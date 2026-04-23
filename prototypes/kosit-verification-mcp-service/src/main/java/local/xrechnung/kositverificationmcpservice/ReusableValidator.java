package local.xrechnung.kositverificationmcpservice;

import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.input.ByteArrayInput;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.io.IOException;
import java.net.URI;
import net.sf.saxon.s9api.Processor;
import org.w3c.dom.Document;

final class ReusableValidator {

  private final DefaultCheck check;
  private final Object lock = new Object();

  private ReusableValidator(DefaultCheck check) {
    this.check = check;
  }

  static ReusableValidator create(ValidatorConfigSupport.PreparedConfiguration configuration)
      throws Exception {
    Processor processor = ProcessorProvider.getProcessor();
    URI repositoryUri = asDirectoryUri(configuration.getConfigDirectory());
    Configuration validatorConfiguration =
        Configuration.load(configuration.getScenariosFile().toUri(), repositoryUri)
            .build(processor);
    return new ReusableValidator(new DefaultCheck(processor, validatorConfiguration));
  }

  ValidationRun validate(byte[] xmlBytes, String documentReference) throws Exception {
    synchronized (lock) {
      Result result = check.checkInput(new ByteArrayInput(xmlBytes, documentReference, "SHA-256"));
      byte[] reportXmlBytes = reportXmlBytes(result.getReportDocument());
      return new ValidationRun(result, reportXmlBytes);
    }
  }

  private static URI asDirectoryUri(java.nio.file.Path directory) {
    String value = directory.toAbsolutePath().toUri().toString();
    return URI.create(value.endsWith("/") ? value : value + "/");
  }

  private static byte[] reportXmlBytes(Document document) throws IOException {
    return document != null ? XmlSupport.toBytes(document) : null;
  }

  static final class ValidationRun {
    private final Result result;
    private final byte[] reportXmlBytes;

    ValidationRun(Result result, byte[] reportXmlBytes) {
      this.result = result;
      this.reportXmlBytes = reportXmlBytes;
    }

    Result getResult() {
      return result;
    }

    byte[] getReportXmlBytes() {
      return reportXmlBytes;
    }
  }
}

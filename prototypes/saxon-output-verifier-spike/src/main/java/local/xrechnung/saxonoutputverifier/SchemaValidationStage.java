package local.xrechnung.saxonoutputverifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

final class SchemaValidationStage {

  private SchemaValidationStage() {
  }

  static Result validate(Path configDirectory, ScenarioDefinition scenario, Path xmlPath)
      throws Exception {
    Path xmlSchema = configDirectory.resolve(scenario.getXmlSchemaLocation()).normalize();
    if (!Files.exists(xmlSchema)) {
      throw new IllegalArgumentException("XML Schema not found: " + xmlSchema);
    }

    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
    Schema schema = schemaFactory.newSchema(xmlSchema.toFile());

    Validator validator = schema.newValidator();
    validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
    CollectingErrorHandler errorHandler = new CollectingErrorHandler();
    validator.setErrorHandler(errorHandler);

    try {
      validator.validate(new StreamSource(xmlPath.toFile()));
    } catch (SAXException e) {
      if (errorHandler.hasFindings()) {
        return new Result(xmlSchema, !errorHandler.hasErrors(), errorHandler.getFindings());
      }
      throw e;
    }

    return new Result(xmlSchema, !errorHandler.hasErrors(), errorHandler.getFindings());
  }

  static final class Result {
    private final Path xmlSchema;
    private final boolean valid;
    private final List<VerifierFinding> findings;

    Result(Path xmlSchema, boolean valid, List<VerifierFinding> findings) {
      this.xmlSchema = xmlSchema;
      this.valid = valid;
      this.findings = Collections.unmodifiableList(new ArrayList<VerifierFinding>(findings));
    }

    Path getXmlSchema() {
      return xmlSchema;
    }

    boolean isValid() {
      return valid;
    }

    List<VerifierFinding> getFindings() {
      return findings;
    }
  }

  private static final class CollectingErrorHandler implements ErrorHandler {
    private final List<VerifierFinding> findings = new ArrayList<VerifierFinding>();
    private boolean hasErrors;

    @Override
    public void warning(SAXParseException exception) {
      findings.add(toFinding("warning", exception));
    }

    @Override
    public void error(SAXParseException exception) {
      hasErrors = true;
      findings.add(toFinding("error", exception));
    }

    @Override
    public void fatalError(SAXParseException exception) {
      hasErrors = true;
      findings.add(toFinding("fatal", exception));
    }

    boolean hasErrors() {
      return hasErrors;
    }

    boolean hasFindings() {
      return !findings.isEmpty();
    }

    List<VerifierFinding> getFindings() {
      return findings;
    }

    private static VerifierFinding toFinding(String severity, SAXParseException exception) {
      String location = null;
      if (exception.getSystemId() != null) {
        location = exception.getSystemId();
      }
      return new VerifierFinding(
          "xsd",
          severity,
          exception.getMessage(),
          location,
          null,
          null,
          null,
          null,
          "xsd-validation",
          positiveOrNull(exception.getLineNumber()),
          positiveOrNull(exception.getColumnNumber()),
          "xsd");
    }

    private static Integer positiveOrNull(int value) {
      return value > 0 ? Integer.valueOf(value) : null;
    }
  }
}

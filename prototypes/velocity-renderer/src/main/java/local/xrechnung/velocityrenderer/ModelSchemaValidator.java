package local.xrechnung.velocityrenderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Validates semantic-model YAML or JSON files against the committed JSON Schema.
 */
final class ModelSchemaValidator {

  private static final String SCHEMA_RELATIVE_PATH = "semantic-model/xrechnung.schema.json";

  private ModelSchemaValidator() {
  }

  static ValidationResult validate(Path projectRoot, Path modelPath) throws IOException {
    Path absoluteModelPath = modelPath.toAbsolutePath();
    Path schemaPath = projectRoot.resolve(SCHEMA_RELATIVE_PATH).toAbsolutePath();
    if (!Files.exists(schemaPath)) {
      throw new IOException(
          "Model schema not found at " + schemaPath
              + ". Expected the committed semantic-model/xrechnung.schema.json.");
    }

    JsonNode schemaNode;
    try (InputStream input = Files.newInputStream(schemaPath)) {
      schemaNode = new ObjectMapper().readTree(input);
    }

    JsonNode modelNode = InvoiceModelLoader.readRootNode(absoluteModelPath);
    JsonSchemaFactory schemaFactory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    JsonSchema schema = schemaFactory.getSchema(schemaNode);
    Set<ValidationMessage> messages = schema.validate(modelNode);

    List<String> renderedMessages = new ArrayList<String>();
    for (ValidationMessage message : messages) {
      String path = message.getInstanceLocation().toString();
      if (path == null || path.trim().isEmpty()) {
        path = "$";
      }
      renderedMessages.add(absoluteModelPath + ": " + path + " " + message.getMessage());
    }
    Collections.sort(renderedMessages);
    return new ValidationResult(schemaPath, renderedMessages);
  }

  static final class ValidationResult {
    private final Path schemaPath;
    private final List<String> messages;

    ValidationResult(Path schemaPath, List<String> messages) {
      this.schemaPath = schemaPath;
      this.messages = messages;
    }

    Path getSchemaPath() {
      return schemaPath;
    }

    List<String> getMessages() {
      return messages;
    }

    boolean isSuccessful() {
      return messages.isEmpty();
    }
  }
}

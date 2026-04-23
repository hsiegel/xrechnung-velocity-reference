package local.xrechnung.saxonoutputverifier;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ResultJsonWriter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  private ResultJsonWriter() {
  }

  static void write(Path outputPath, VerificationResult result) throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), result);
  }
}

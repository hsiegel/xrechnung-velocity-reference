package local.xrechnung.velocityrenderer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Loads semantic invoice models from YAML or JSON documents with a top-level
 * {@code xr} object.
 */
public final class InvoiceModelLoader {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
  };

  private InvoiceModelLoader() {
  }

  public static Map<String, Object> load(Path modelPath) throws IOException {
    ObjectMapper mapper = objectMapperFor(modelPath);
    JsonNode root = readRootNode(modelPath, mapper);
    JsonNode xr = root.get("xr");
    if (xr == null || !xr.isObject()) {
      throw new IOException("Model file must contain a top-level object field 'xr': " + modelPath.toAbsolutePath());
    }
    return mapper.convertValue(xr, MAP_TYPE);
  }

  static JsonNode readRootNode(Path modelPath) throws IOException {
    return readRootNode(modelPath, objectMapperFor(modelPath));
  }

  static ObjectMapper objectMapperFor(Path modelPath) {
    String fileName = modelPath.getFileName() == null ? modelPath.toString() : modelPath.getFileName().toString();
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
      return new ObjectMapper(new YAMLFactory());
    }
    if (lower.endsWith(".json")) {
      return new ObjectMapper();
    }
    throw new IllegalArgumentException(
        "Unsupported model format for '" + fileName + "'. Use .yaml, .yml, or .json.");
  }

  private static JsonNode readRootNode(Path modelPath, ObjectMapper mapper) throws IOException {
    try (InputStream input = Files.newInputStream(modelPath)) {
      JsonNode root = mapper.readTree(input);
      if (root == null || root.isNull()) {
        throw new IOException("Model file is empty: " + modelPath.toAbsolutePath());
      }
      return root;
    }
  }
}

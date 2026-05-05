package local.xrechnung.kositisolated;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

final class ReflectiveKositValidator {

  private static final String PROCESSOR_PROVIDER =
      "de.kosit.validationtool.impl.xml.ProcessorProvider";
  private static final String PROCESSOR = "net.sf.saxon.s9api.Processor";
  private static final String CONFIGURATION = "de.kosit.validationtool.api.Configuration";
  private static final String DEFAULT_CHECK = "de.kosit.validationtool.impl.DefaultCheck";
  private static final String BYTE_ARRAY_INPUT =
      "de.kosit.validationtool.impl.input.ByteArrayInput";
  private static final Path READY_MARKER =
      Path.of("resources/ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd");

  private final ClassLoader isolatedClassLoader;

  ReflectiveKositValidator(ClassLoader isolatedClassLoader) {
    this.isolatedClassLoader = isolatedClassLoader;
  }

  Map<String, Object> verify(Map<String, String> request) throws Exception {
    Path xmlPath = requirePath(request, "xmlPath");
    Path configPath = requirePath(request, "configPath");
    Path workDir = requirePath(request, "workDir");
    Path reportOut = requirePath(request, "reportOut");
    boolean diagnostics = Boolean.parseBoolean(request.get("diagnostics"));

    byte[] xmlBytes = Files.readAllBytes(xmlPath);
    PreparedConfiguration configuration = prepareConfiguration(configPath, workDir);
    Object result = runKosit(xmlBytes, xmlPath, configuration);

    Path writtenReport = null;
    Object reportDocument = invokeNoArg(result, "getReportDocument");
    if (reportDocument instanceof Document) {
      writeReport((Document) reportDocument, reportOut);
      writtenReport = reportOut;
    }

    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("status", statusOf(result));
    output.put("xmlPath", xmlPath.toString());
    output.put("configPath", configPath.toString());
    output.put("workDir", workDir.toString());
    output.put("configurationDirectory", configuration.configDirectory.toString());
    output.put("reportPath", writtenReport != null ? writtenReport.toString() : null);
    if (request.containsKey("stageRoot")) {
      output.put("stageRoot", request.get("stageRoot"));
    }
    output.put("acceptRecommendation", String.valueOf(invokeNoArg(result, "getAcceptRecommendation")));
    output.put("processingSuccessful", Boolean.valueOf(booleanValue(result, "isProcessingSuccessful")));
    output.put("wellformed", Boolean.valueOf(booleanValue(result, "isWellformed")));
    output.put("schemaValid", Boolean.valueOf(booleanValue(result, "isSchemaValid")));
    output.put("schematronValid", Boolean.valueOf(booleanValue(result, "isSchematronValid")));
    output.put("messages", messagesOf(result));
    if (diagnostics) {
      output.put("diagnostics", KositRuntimeDiagnostics.collect(isolatedClassLoader));
    }
    return output;
  }

  Map<String, Object> diagnostics(Map<String, String> request) {
    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("status", "DIAGNOSTICS");
    output.put("runtimeLibDir", request.get("runtimeLibDir"));
    output.put("configPath", request.get("configPath"));
    output.put("workDir", request.get("workDir"));
    if (request.containsKey("stageRoot")) {
      output.put("stageRoot", request.get("stageRoot"));
    }
    output.put("messages", new ArrayList<Map<String, Object>>());
    output.put("diagnostics", KositRuntimeDiagnostics.collect(isolatedClassLoader));
    return output;
  }

  private Object runKosit(
      byte[] xmlBytes,
      Path xmlPath,
      PreparedConfiguration configuration) throws Exception {
    Class<?> processorProviderClass = load(PROCESSOR_PROVIDER);
    Class<?> processorClass = load(PROCESSOR);
    Class<?> configurationClass = load(CONFIGURATION);
    Class<?> defaultCheckClass = load(DEFAULT_CHECK);
    Class<?> byteArrayInputClass = load(BYTE_ARRAY_INPUT);

    Object processor = invoke(processorProviderClass.getMethod("getProcessor"), null);
    Object configurationLoader = invoke(
        configurationClass.getMethod("load", URI.class, URI.class),
        null,
        configuration.scenariosFile.toUri(),
        asDirectoryUri(configuration.configDirectory));
    Method buildMethod = compatibleMethod(configurationLoader.getClass(), "build", processor);
    Object validatorConfiguration = invoke(buildMethod, configurationLoader, processor);
    Object validatorConfigurations = Array.newInstance(configurationClass, 1);
    Array.set(validatorConfigurations, 0, validatorConfiguration);
    Object check = newInstance(defaultCheckClass, processor, validatorConfigurations);
    Object input = newInstance(
        byteArrayInputClass,
        xmlBytes,
        xmlPath.toUri().toString(),
        "SHA-256");
    return invoke(compatibleMethod(check.getClass(), "checkInput", input), check, input);
  }

  private Class<?> load(String className) throws ClassNotFoundException {
    return Class.forName(className, true, isolatedClassLoader);
  }

  private static String statusOf(Object result) throws Exception {
    if (!booleanValue(result, "isProcessingSuccessful")) {
      return "ERROR";
    }
    return booleanValue(result, "isAcceptable") ? "ACCEPTED" : "REJECTED";
  }

  private static List<Map<String, Object>> messagesOf(Object result) throws Exception {
    List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    for (Object processingError : valuesOf(invokeNoArg(result, "getProcessingErrors"))) {
      Map<String, Object> message = baseMessage("processing", "error", String.valueOf(processingError));
      messages.add(message);
    }
    for (Object schemaViolation : valuesOf(invokeNoArg(result, "getSchemaViolations"))) {
      Map<String, Object> message = baseMessage(
          "xsd",
          String.valueOf(invokeNoArg(schemaViolation, "getSeverity")),
          String.valueOf(invokeNoArg(schemaViolation, "getMessage")));
      message.put("line", invokeNoArg(schemaViolation, "getRowNumber"));
      message.put("column", invokeNoArg(schemaViolation, "getColumnNumber"));
      messages.add(message);
    }
    for (Object failedAssert : valuesOf(invokeNoArg(result, "getFailedAsserts"))) {
      Map<String, Object> message = baseMessage(
          "schematron",
          stringOrDefault(invokeNoArg(failedAssert, "getFlag"), "failed-assert"),
          flattenText(invokeNoArg(failedAssert, "getText")));
      message.put("location", invokeNoArg(failedAssert, "getLocation"));
      message.put("ruleId", invokeNoArg(failedAssert, "getId"));
      message.put("test", invokeNoArg(failedAssert, "getTest"));
      message.put("flag", invokeNoArg(failedAssert, "getFlag"));
      message.put("role", invokeNoArg(failedAssert, "getRole"));
      messages.add(message);
    }
    return messages;
  }

  private static List<?> valuesOf(Object rawValue) {
    if (rawValue == null) {
      return List.of();
    }
    if (rawValue instanceof List<?>) {
      return (List<?>) rawValue;
    }
    if (rawValue instanceof Collection<?>) {
      return new ArrayList<Object>((Collection<?>) rawValue);
    }
    return List.of(rawValue);
  }

  private static Map<String, Object> baseMessage(
      String channel,
      String severity,
      String messageText) {
    Map<String, Object> message = new LinkedHashMap<String, Object>();
    message.put("channel", channel);
    message.put("severity", severity);
    message.put("message", messageText);
    return message;
  }

  private static String flattenText(Object text) throws Exception {
    if (text == null) {
      return "";
    }
    Object rawContent = invokeNoArg(text, "getContent");
    if (!(rawContent instanceof Collection<?>)) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (Object part : (Collection<?>) rawContent) {
      String chunk = String.valueOf(part).trim();
      if (!chunk.isEmpty()) {
        if (builder.length() > 0) {
          builder.append(' ');
        }
        builder.append(chunk);
      }
    }
    return builder.toString();
  }

  private static String stringOrDefault(Object value, String fallback) {
    return value != null ? String.valueOf(value) : fallback;
  }

  private static PreparedConfiguration prepareConfiguration(Path configZip, Path workDir)
      throws IOException {
    Files.createDirectories(workDir);
    Path configDirectory = workDir.resolve(stripZipSuffix(configZip.getFileName().toString()));
    extractZipIfNeeded(configZip, configDirectory, READY_MARKER);
    Path scenariosFile = configDirectory.resolve("scenarios.xml");
    if (!Files.isRegularFile(scenariosFile)) {
      throw new IOException("Missing scenarios.xml in " + configDirectory);
    }
    return new PreparedConfiguration(configDirectory.toAbsolutePath(), scenariosFile.toAbsolutePath());
  }

  private static void extractZipIfNeeded(
      Path zipPath,
      Path targetDir,
      Path markerRelativePath) throws IOException {
    // Spike shortcut: production should verify a bundle hash or manifest here.
    if (Files.exists(targetDir.resolve(markerRelativePath))) {
      return;
    }

    Path parentDir = targetDir.getParent();
    if (parentDir == null) {
      throw new IOException("Missing parent directory for " + targetDir);
    }
    Files.createDirectories(parentDir);

    Path tempDir = Files.createTempDirectory(parentDir, targetDir.getFileName().toString() + ".tmp-");
    try (InputStream rawStream = Files.newInputStream(zipPath);
         ZipInputStream zipStream = new ZipInputStream(rawStream, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zipStream.getNextEntry()) != null) {
        Path outputPath = tempDir.resolve(entry.getName()).normalize();
        if (!outputPath.startsWith(tempDir)) {
          throw new IOException("Blocked suspicious ZIP entry " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(outputPath);
        } else {
          Path outputParent = outputPath.getParent();
          if (outputParent != null) {
            Files.createDirectories(outputParent);
          }
          Files.copy(zipStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zipStream.closeEntry();
      }
    } catch (IOException e) {
      deleteRecursively(tempDir);
      throw e;
    }

    deleteRecursively(targetDir);
    try {
      Files.move(tempDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      deleteRecursively(tempDir);
      if (!Files.exists(targetDir.resolve(markerRelativePath))) {
        throw e;
      }
    }
  }

  private static void writeReport(Document document, Path reportPath) throws Exception {
    Path parent = reportPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
    transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    try (OutputStream output = Files.newOutputStream(reportPath)) {
      transformer.transform(new DOMSource(document), new StreamResult(output));
    }
  }

  private static URI asDirectoryUri(Path directory) {
    String value = directory.toAbsolutePath().toUri().toString();
    return URI.create(value.endsWith("/") ? value : value + "/");
  }

  private static Path requirePath(Map<String, String> request, String key) {
    String value = request.get(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing reflective request value " + key);
    }
    return Path.of(value).toAbsolutePath().normalize();
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static String stripZipSuffix(String name) {
    if (name.endsWith(".zip")) {
      return name.substring(0, name.length() - 4);
    }
    return name;
  }

  private static Object newInstance(Class<?> type, Object... args) throws Exception {
    Constructor<?> constructor = compatibleConstructor(type, args);
    return invoke(constructor, args);
  }

  private static Constructor<?> compatibleConstructor(Class<?> type, Object... args)
      throws NoSuchMethodException {
    for (Constructor<?> constructor : type.getConstructors()) {
      if (isCompatible(constructor.getParameterTypes(), args)) {
        return constructor;
      }
    }
    throw new NoSuchMethodException("No compatible constructor on " + type.getName());
  }

  private static Method compatibleMethod(Class<?> type, String name, Object... args)
      throws NoSuchMethodException {
    for (Method method : type.getMethods()) {
      if (method.getName().equals(name)
          && Modifier.isPublic(method.getModifiers())
          && isCompatible(method.getParameterTypes(), args)) {
        return method;
      }
    }
    throw new NoSuchMethodException("No compatible method " + name + " on " + type.getName());
  }

  private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
    if (parameterTypes.length != args.length) {
      return false;
    }
    for (int index = 0; index < parameterTypes.length; index++) {
      Object arg = args[index];
      Class<?> parameterType = wrap(parameterTypes[index]);
      if (arg == null) {
        if (parameterTypes[index].isPrimitive()) {
          return false;
        }
      } else if (!parameterType.isAssignableFrom(arg.getClass())) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> wrap(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    return Void.class;
  }

  private static Object invokeNoArg(Object target, String methodName) throws Exception {
    return invoke(target.getClass().getMethod(methodName), target);
  }

  private static Object invoke(Method method, Object target, Object... args) throws Exception {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw causeOf(e);
    }
  }

  private static Object invoke(Constructor<?> constructor, Object... args) throws Exception {
    try {
      return constructor.newInstance(args);
    } catch (InvocationTargetException e) {
      throw causeOf(e);
    }
  }

  private static Exception causeOf(InvocationTargetException error) {
    Throwable cause = error.getCause();
    if (cause instanceof RuntimeException) {
      throw (RuntimeException) cause;
    }
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    if (cause instanceof Exception) {
      return (Exception) cause;
    }
    return new RuntimeException(cause);
  }

  private static boolean booleanValue(Object target, String methodName) throws Exception {
    Object value = invokeNoArg(target, methodName);
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static final class PreparedConfiguration {
    private final Path configDirectory;
    private final Path scenariosFile;

    private PreparedConfiguration(Path configDirectory, Path scenariosFile) {
      this.configDirectory = configDirectory;
      this.scenariosFile = scenariosFile;
    }
  }
}

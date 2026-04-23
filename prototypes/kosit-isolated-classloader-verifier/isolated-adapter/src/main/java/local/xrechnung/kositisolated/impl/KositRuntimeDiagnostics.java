package local.xrechnung.kositisolated.impl;

import java.lang.reflect.Method;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

final class KositRuntimeDiagnostics {

  private static final String[] CLASSES_TO_CHECK = {
      "de.kosit.validationtool.api.Check",
      "de.kosit.validationtool.impl.DefaultCheck",
      "net.sf.saxon.Version",
      "org.xmlresolver.Resolver",
      "org.apache.commons.lang3.StringUtils",
      "org.apache.commons.io.IOUtils",
      "jakarta.xml.bind.JAXBContext",
      "org.slf4j.LoggerFactory"
  };

  private KositRuntimeDiagnostics() {
  }

  static Map<String, Object> collect(ClassLoader isolatedClassLoader) {
    Map<String, Object> diagnostics = new LinkedHashMap<String, Object>();
    diagnostics.put(
        "threadContextClassLoader",
        describeClassLoader(Thread.currentThread().getContextClassLoader()));
    diagnostics.put(
        "diagnosticsClassLoader",
        describeClassLoader(KositRuntimeDiagnostics.class.getClassLoader()));
    List<Map<String, Object>> classes = new ArrayList<Map<String, Object>>();
    for (String className : CLASSES_TO_CHECK) {
      classes.add(describeClass(className, isolatedClassLoader));
    }
    diagnostics.put("classes", classes);
    diagnostics.put("jaxpFactories", describeJaxpFactories());
    return diagnostics;
  }

  private static Map<String, Object> describeClass(String className, ClassLoader classLoader) {
    Map<String, Object> description = new LinkedHashMap<String, Object>();
    description.put("name", className);
    try {
      Class<?> type = Class.forName(className, false, classLoader);
      description.put("loaded", Boolean.TRUE);
      description.put("classLoader", describeClassLoader(type.getClassLoader()));
      description.put("codeSource", describeCodeSource(type));
      description.put("version", versionOf(type));
      if ("net.sf.saxon.Version".equals(className)) {
        description.put("productVersion", saxonProductVersion(type));
      }
    } catch (ClassNotFoundException e) {
      description.put("loaded", Boolean.FALSE);
      description.put("error", e.getClass().getSimpleName());
    } catch (RuntimeException e) {
      description.put("loaded", Boolean.FALSE);
      description.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (LinkageError e) {
      description.put("loaded", Boolean.FALSE);
      description.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return description;
  }

  private static List<Map<String, Object>> describeJaxpFactories() {
    List<Map<String, Object>> factories = new ArrayList<Map<String, Object>>();
    factories.add(describeFactory(
        "javax.xml.transform.TransformerFactory.newInstance()",
        new FactorySupplier() {
          @Override
          public Class<?> get() {
            return TransformerFactory.newInstance().getClass();
          }
        }));
    factories.add(describeFactory(
        "javax.xml.parsers.DocumentBuilderFactory.newInstance()",
        new FactorySupplier() {
          @Override
          public Class<?> get() {
            return DocumentBuilderFactory.newInstance().getClass();
          }
        }));
    factories.add(describeFactory(
        "javax.xml.validation.SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)",
        new FactorySupplier() {
          @Override
          public Class<?> get() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass();
          }
        }));
    return factories;
  }

  private static Map<String, Object> describeFactory(String name, FactorySupplier supplier) {
    Map<String, Object> description = new LinkedHashMap<String, Object>();
    description.put("name", name);
    try {
      Class<?> implementationClass = supplier.get();
      description.put("loaded", Boolean.TRUE);
      description.put("implementationClass", implementationClass.getName());
      description.put("classLoader", describeClassLoader(implementationClass.getClassLoader()));
      description.put("codeSource", describeCodeSource(implementationClass));
      description.put("version", versionOf(implementationClass));
    } catch (RuntimeException e) {
      description.put("loaded", Boolean.FALSE);
      description.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (LinkageError e) {
      description.put("loaded", Boolean.FALSE);
      description.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return description;
  }

  private static String describeClassLoader(ClassLoader classLoader) {
    if (classLoader == null) {
      return "bootstrap";
    }
    return classLoader.getClass().getName() + "@"
        + Integer.toHexString(System.identityHashCode(classLoader));
  }

  private static String describeCodeSource(Class<?> type) {
    CodeSource codeSource = type.getProtectionDomain() != null
        ? type.getProtectionDomain().getCodeSource()
        : null;
    return codeSource != null && codeSource.getLocation() != null
        ? codeSource.getLocation().toString()
        : null;
  }

  private static String versionOf(Class<?> type) {
    Package packageInfo = type.getPackage();
    if (packageInfo == null) {
      return null;
    }
    if (packageInfo.getImplementationVersion() != null) {
      return packageInfo.getImplementationVersion();
    }
    return packageInfo.getSpecificationVersion();
  }

  private static String saxonProductVersion(Class<?> type) {
    try {
      Method method = type.getMethod("getProductVersion");
      Object value = method.invoke(null);
      return value != null ? String.valueOf(value) : null;
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private interface FactorySupplier {

    Class<?> get();
  }
}

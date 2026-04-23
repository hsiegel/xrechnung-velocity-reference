package local.xrechnung.kositisolated;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

public final class ChildFirstUrlClassLoader extends URLClassLoader {

  private static final List<String> PARENT_FIRST_PREFIXES = Arrays.asList(
      "java.",
      "javax.",
      "sun.",
      "com.sun.",
      "jdk.",
      "local.xrechnung.kositisolated.bridge.");

  private static final List<String> CHILD_FIRST_PREFIXES = Arrays.asList(
      "local.xrechnung.kositisolated.impl.",
      "de.kosit.",
      "net.sf.saxon.",
      "org.xmlresolver.",
      "org.apache.commons.lang3.",
      "org.apache.commons.io.",
      "jakarta.xml.bind.",
      "org.glassfish.jaxb.",
      "com.sun.xml.bind.",
      "com.sun.istack.",
      "org.slf4j.",
      "org.oclc.purl.dsdl.svrl.");

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> loadedClass = findLoadedClass(name);
      if (loadedClass == null) {
        loadedClass = loadUncachedClass(name);
      }
      if (resolve) {
        resolveClass(loadedClass);
      }
      return loadedClass;
    }
  }

  private Class<?> loadUncachedClass(String name) throws ClassNotFoundException {
    if (isChildFirst(name)) {
      Class<?> childClass = findClassOrNull(name);
      if (childClass != null) {
        return childClass;
      }
      return loadFromParent(name);
    }

    if (isParentFirst(name)) {
      return loadFromParent(name);
    }

    try {
      return loadFromParent(name);
    } catch (ClassNotFoundException e) {
      Class<?> childClass = findClassOrNull(name);
      if (childClass != null) {
        return childClass;
      }
      throw e;
    }
  }

  private Class<?> loadFromParent(String name) throws ClassNotFoundException {
    return getParent() != null ? getParent().loadClass(name) : findSystemClass(name);
  }

  private Class<?> findClassOrNull(String name) throws ClassNotFoundException {
    try {
      return findClass(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static boolean isParentFirst(String name) {
    return startsWithAny(name, PARENT_FIRST_PREFIXES);
  }

  private static boolean isChildFirst(String name) {
    return startsWithAny(name, CHILD_FIRST_PREFIXES);
  }

  private static boolean startsWithAny(String name, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}

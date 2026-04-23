package local.xrechnung.saxonoutputverifier;

import java.nio.file.Path;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;

final class ScenarioMatcher {

  private ScenarioMatcher() {
  }

  static boolean matches(Path xmlPath, ScenarioDefinition scenario) throws Exception {
    Processor processor = new Processor(false);
    DocumentBuilder builder = processor.newDocumentBuilder();
    XdmNode document = builder.build(new StreamSource(xmlPath.toFile()));

    XPathCompiler compiler = processor.newXPathCompiler();
    for (Map.Entry<String, String> namespace : scenario.getNamespaces().entrySet()) {
      compiler.declareNamespace(namespace.getKey(), namespace.getValue());
    }

    XPathExecutable executable = compiler.compile(scenario.getMatchExpression());
    XPathSelector selector = executable.load();
    selector.setContextItem(document);
    return selector.effectiveBooleanValue();
  }
}

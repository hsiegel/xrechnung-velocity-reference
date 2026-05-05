# ClassLoader Strategy

Dieser Prototyp zeigt Same-VM-Isolation: Der Host startet keinen Prozess und
keinen Dienst, sondern laedt die KoSIT-Welt zur Laufzeit aus einem lokalen
Staging-Verzeichnis in einen eigenen `URLClassLoader`.

## Artefakte

- `target/kosit-isolated-classloader-verifier.jar`
  enthaelt Host-Code, Jackson fuer Host-JSON und die KoSIT-Runtime als
  Classpath-Ressourcen.
- `kosit-isolated/runtime-lib/*.jar`
  liegt im Host-Jar und enthaelt KoSIT `1.6.2` samt Runtime-Dependencies.
- `kosit-isolated/runtime-jars.list`
  liegt im Host-Jar und listet die Ressourcen-Jars, die gestaged werden muessen.
- `kosit-isolated/config/*.zip`
  liegt im Host-Jar und enthaelt die XRechnung-Validator-Konfiguration.
- `target/kosit-runtime/lib/*.jar`
  enthaelt dieselben KoSIT-Runtime-Jars fuer den expliziten
  Dateisystem-Modus.

Der Host-Code hat keine KoSIT-, Saxon-, JAXB- oder XMLResolver-Dependency im
Compile-Classpath. Das Maven-Modul deklariert diese Abhaengigkeiten mit
Runtime-Scope, damit sie beim Build als Classpath-Ressourcen und optional nach
`target/kosit-runtime/lib/` kopiert werden. Der Aufruf der KoSIT-API passiert
anschliessend reflektiv.

## Classpath-Staging

Wenn weder `--runtime-lib-dir` noch `--config` gesetzt sind, nutzt die CLI den
Classpath-Ressourcenmodus:

- `ClasspathRuntimeStager` liest `kosit-isolated/runtime-jars.list`.
- Jede dort genannte Jar wird aus `kosit-isolated/runtime-lib/` in
  `<stage>/runtime-lib/` kopiert.
- Das Konfigurations-ZIP wird aus `kosit-isolated/config/` nach
  `<stage>/config/` kopiert.
- Das Arbeitsverzeichnis fuer die entpackte Konfiguration ist per Default
  `<stage>/validator-work/`.
- Der isolierte `URLClassLoader` bekommt nur URLs auf die kopierten Jars im
  Staging-Verzeichnis.

Der Default-Stage liegt in einem temporaeren Verzeichnis und wird in der JVM
gecached. Mit `--stage-dir` kann ein stabiler, lokal kontrollierter Pfad
vorgegeben werden. Fuer den Produktionsbetrieb sollte dieser Pfad knotenlokal,
schreibbar und in die lokale Cleanup-Strategie eingebunden sein.

## Parent-First

Diese Pakete werden parent-first geladen:

- `java.`
- `javax.`
- `sun.`
- `com.sun.`
- `jdk.`

## Child-First

Diese Pakete werden bevorzugt aus dem isolierten Runtime-Verzeichnis geladen:

- `de.kosit.`
- `net.sf.saxon.`
- `org.xmlresolver.`
- `org.apache.commons.lang3.`
- `org.apache.commons.io.`
- `jakarta.xml.bind.`
- `org.glassfish.jaxb.`
- `com.sun.xml.bind.`
- `com.sun.istack.`
- `org.slf4j.`
- `org.oclc.purl.dsdl.svrl.`

Spezifische child-first-Pakete gewinnen gegen breitere parent-first-Pakete. Das
ist fuer `com.sun.xml.bind.` und `com.sun.istack.` bewusst so, weil
JAXB-Implementierungsdetails zur isolierten KoSIT-Laufzeit gehoeren.

## SLF4J

SLF4J ist besonders heikel, weil Host-Anwendung und isolierte KoSIT-Laufzeit
unterschiedliche API- oder Binding-Kombinationen mitbringen koennen. Der Spike
laedt deshalb `org.slf4j.` aktuell bewusst child-first und zeigt bei
`--diagnostics`, aus welcher Jar `org.slf4j.LoggerFactory` wirklich kommt.
`slf4j-simple` ist dabei nur ein Prototypen-Binding, damit die isolierte
Runtime ohne Host-Logging-Integration lauffaehig ist.

Fuer Produktion ist damit noch keine Logging-Strategie entschieden. Offen ist
vor allem:

- Logging komplett innerhalb der isolierten KoSIT-Welt halten.
- KoSIT-Logs kontrolliert in den Host-Logging-Stack bruecken.
- Den Host-Logging-Stack insgesamt auf kompatible Versionen bringen.

## Thread Context ClassLoader

Der Host setzt waehrend des reflektiven KoSIT-Aufrufs den Thread Context
ClassLoader auf den isolierten ClassLoader und stellt ihn danach wieder her. Das
ist wichtig fuer ServiceLoader-, JAXP-, JAXB- und Logging-Mechanismen, die nicht
nur den definierenden ClassLoader einer Klasse, sondern den Context ClassLoader
nutzen.

Produktionscode muesste diese Regel auch bei Worker-Threads, Pools und
parallelen Validierungen konsequent durchhalten.

## Diagnose

Mit `--diagnostics` prueft der Host ueber den isolierten ClassLoader
exemplarisch diese Klassen:

- `de.kosit.validationtool.api.Check`
- `de.kosit.validationtool.impl.DefaultCheck`
- `net.sf.saxon.Version`
- `org.xmlresolver.Resolver`
- `org.apache.commons.lang3.StringUtils`
- `org.apache.commons.io.IOUtils`
- `jakarta.xml.bind.JAXBContext`
- `org.slf4j.LoggerFactory`

Fuer jede Klasse werden ClassLoader, CodeSource und nach Moeglichkeit die
Version ausgegeben. Bei Saxon wird zusaetzlich `Version.getProductVersion()`
ausgelesen, damit sichtbar ist, dass nicht versehentlich eine global
bereitgestellte Saxon-Version verwendet wird.

Im Classpath-Ressourcenmodus sollten die CodeSources auf das Staging zeigen,
etwa `file:/tmp/kosit-isolated-runtime-.../runtime-lib/Saxon-HE-12.9.jar`. Im
expliziten Dateisystem-Modus sollten sie auf das uebergebene
`--runtime-lib-dir` zeigen.

Zusaetzlich werden der aktuelle
`Thread.currentThread().getContextClassLoader()` und exemplarische
JAXP-Factory-Aufloesungen dokumentiert:

- `TransformerFactory.newInstance().getClass()`
- `DocumentBuilderFactory.newInstance().getClass()`
- `SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass()`

Mit `--diagnostics-only` wird nur die isolierte Runtime geladen und diese
Diagnose erzeugt. Das ist der kleinste Smoke-Test fuer die ClassLoader-Grenze:
keine XML, keine Bundle-Extraktion, keine KoSIT-Validierung.

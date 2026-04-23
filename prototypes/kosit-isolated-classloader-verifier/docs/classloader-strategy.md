# ClassLoader Strategy

Dieser Prototyp zeigt Same-VM-Isolation: Der Host startet keinen Prozess und
keinen Dienst, sondern laedt die KoSIT-Welt zur Laufzeit aus
`target/kosit-runtime/lib/` in einen eigenen `URLClassLoader`.

## Artefakte

- `target/kosit-isolated-classloader-verifier.jar`
  enthaelt Host-Code plus das kleine Bridge-Interface.
- `target/kosit-runtime/lib/kosit-isolated-adapter.jar`
  enthaelt die Implementierung der Bridge.
- `target/kosit-runtime/lib/*.jar`
  enthaelt KoSIT `1.6.2` und dessen Runtime-Dependencies, darunter Saxon-HE
  `12.9`, XMLResolver, JAXB `4.x`, commons-lang3, commons-io und SLF4J.

Der Host-Code hat keine KoSIT-, Saxon-, JAXB- oder XMLResolver-Dependency im
eigenen Maven-Modul. Nur das Adapter-Modul kompiliert gegen diese Bibliotheken.

## Parent-First

Diese Pakete werden parent-first geladen:

- `java.`
- `javax.`
- `sun.`
- `com.sun.`
- `jdk.`
- `local.xrechnung.kositisolated.bridge.`

Die Bridge muss parent-first bleiben, damit Host und isolierter Adapter
dasselbe Interface sehen.

## Child-First

Diese Pakete werden bevorzugt aus dem isolierten Runtime-Verzeichnis geladen:

- `local.xrechnung.kositisolated.impl.`
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

Der Host setzt waehrend des Bridge-Aufrufs den Thread Context ClassLoader auf
den isolierten ClassLoader und stellt ihn danach wieder her. Das ist wichtig
fuer ServiceLoader-, JAXP-, JAXB- und Logging-Mechanismen, die nicht nur den
definierenden ClassLoader einer Klasse, sondern den Context ClassLoader nutzen.

Produktionscode muesste diese Regel auch bei Worker-Threads, Pools und
parallelen Validierungen konsequent durchhalten.

## Diagnose

Mit `--diagnostics` prueft der Adapter exemplarisch diese Klassen:

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

Zusaetzlich werden der aktuelle
`Thread.currentThread().getContextClassLoader()` und exemplarische
JAXP-Factory-Aufloesungen dokumentiert:

- `TransformerFactory.newInstance().getClass()`
- `DocumentBuilderFactory.newInstance().getClass()`
- `SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass()`

Mit `--diagnostics-only` wird nur die isolierte Runtime geladen und diese
Diagnose erzeugt. Das ist der kleinste Smoke-Test fuer die ClassLoader-Grenze:
keine XML, keine Bundle-Extraktion, keine KoSIT-Validierung.

# KoSIT Isolated ClassLoader Verifier

Dieser Prototyp zeigt, wie ein Host-Java-Programm KoSIT Validator `1.6.2`
innerhalb derselben JVM ausfuehren kann, ohne KoSIT, Saxon, JAXB oder
XMLResolver im normalen Host-Classpath zu haben.

Die KoSIT-Welt liegt im ausfuehrbaren Host-Jar als Classpath-Ressource:
KoSIT- und Runtime-Jars unter `kosit-isolated/runtime-lib/`, die
XRechnung-Konfiguration als ZIP unter `kosit-isolated/config/`. Beim ersten
Zugriff werden diese Ressourcen in ein Staging-Verzeichnis auf dem lokalen
Dateisystem kopiert. Erst diese kopierten Jars werden in einen eigenen
`URLClassLoader` aufgenommen. Der Host ruft die benoetigten KoSIT-Klassen
direkt reflektiv aus diesem isolierten ClassLoader auf.

## Zweck

Der Spike ist fuer Host-Umgebungen interessant, in denen der globale Classpath
abweichende XML- oder Logging-Bibliotheken enthalten kann, waehrend KoSIT `1.6.2`
moderne Dependencies wie Saxon-HE `12.9`, JAXB `4.x` und SLF4J `2.x` braucht.

Der Nachweis ist bewusst eng:

- eine JVM
- kein Kindprozess
- kein HTTP
- kein MCP
- Host-Code ohne KoSIT-Compile-Imports
- KoSIT-Jars nur als Ressourcen oder gestagte Dateien, nicht als Host-Klassen
- kein selbst gebautes Adapter- oder Stub-Jar

## Was dieser Spike beweist / was er nicht beweist

Der Spike beweist:

- Das Host-Jar startet ohne KoSIT, Saxon, JAXB, XMLResolver oder SLF4J im
  normalen Host-Classpath.
- Die KoSIT-API wird reflektiv aus einem gestagten Runtime-Verzeichnis geladen
  und aufgerufen.
- KoSIT-Runtime-Jars und Konfigurations-ZIP koennen aus Classpath-Ressourcen
  materialisiert werden, wenn die urspruenglichen Dateien nicht direkt als
  normale Files vorliegen.
- KoSIT-nahe Konfliktklassen wie `net.sf.saxon.Version`,
  `jakarta.xml.bind.JAXBContext` und `org.slf4j.LoggerFactory` kommen aus dem
  isolierten gestagten Runtime-Verzeichnis.
- JAXP-Factories werden waehrend des isolierten Aufrufs mit gesetztem Thread
  Context ClassLoader diagnostiziert.
- Eine Rechnung kann in derselben JVM validiert und als KoSIT-XML-Report
  geschrieben werden.

Der Spike beweist noch nicht:

- dass Wiederverwendung, Pooling oder parallele Validierungen produktionsreif
  thread-safe sind.
- dass Bundle-Reloads ohne ClassLoader-Leaks geloest sind.
- dass Logging-Integration schon final entschieden ist.
- dass Timeouts und harte Fehlerisolation so stark sind wie bei einer
  Prozessgrenze.
- dass austauschbare Runtime- oder Bundle-Verzeichnisse bereits ausreichend
  gegen Manipulation abgesichert sind.
- dass die reflektiv genutzten KoSIT-Signaturen ueber andere KoSIT-Versionen
  hinweg stabil bleiben.

## Unterschied zum Embedded Verifier

[`../kosit-embedded-verifier/`](../kosit-embedded-verifier/) linkt direkt gegen
KoSIT und hat KoSIT/Saxon/JAXB im normalen Maven- und Runtime-Classpath dieses
Prototyps.

Dieser Prototyp trennt dagegen:

- `host/`
  baut das ausfuehrbare CLI-Jar. Der Code importiert keine KoSIT-, Saxon-,
  JAXB- oder XMLResolver-Klassen, sondern greift darauf nur ueber Reflection
  zu. Jackson liegt im Host-Jar, weil JSON auf der Host-Seite erzeugt wird.
- `kosit-isolated/runtime-lib/`
  liegt im Host-Jar als Ressourcenverzeichnis und enthaelt die KoSIT-Runtime-
  Jars, die beim ersten Zugriff in ein lokales Staging-Verzeichnis kopiert
  werden.
- `kosit-isolated/config/`
  liegt ebenfalls als Ressource im Host-Jar und enthaelt das versionierte
  Validator-Konfigurations-ZIP.
- `target/kosit-runtime/lib/`
  enthaelt nach dem Build weiterhin KoSIT `1.6.2` und dessen
  Runtime-Dependencies. Dieser Pfad ist ein expliziter Dateisystem-Modus fuer
  lokale Vergleiche und Integrationen, die die Jars bewusst ausserhalb des
  Host-Jars bereitstellen wollen.

Das `host`-Modul deklariert die KoSIT-Abhaengigkeiten mit Runtime-Scope nur,
damit Maven die passenden Jars als Ressourcen und fuer den optionalen
Dateisystem-Modus kopieren kann. Die KoSIT-Klassen werden nicht in das
ausfuehrbare Host-Jar geshaded.

## Zentrale Klassen

- `IsolatedKositVerifierCli`
  koordiniert den Host-Ablauf: CLI-Optionen lesen, Runtime-Umgebung bestimmen,
  isolierten ClassLoader erstellen, Thread Context ClassLoader setzen und nach
  dem Aufruf wieder herstellen.

  Skizze: Ohne `--runtime-lib-dir` und ohne `--config` wird der
  Classpath-Ressourcenmodus verwendet. Dann ruft die CLI
  `ClasspathRuntimeStager` auf und uebergibt anschliessend die gestagten Pfade
  an `ReflectiveKositValidator`. Mit expliziten Pfaden bleibt der alte
  Dateisystem-Modus erhalten.
- `ClasspathRuntimeStager`
  materialisiert die im Host-Jar liegenden Ressourcen in ein lokales
  Staging-Verzeichnis.

  Skizze: Die Klasse liest `kosit-isolated/runtime-jars.list`, kopiert jede
  genannte Jar aus `kosit-isolated/runtime-lib/` nach `runtime-lib/`, kopiert
  das Konfigurations-ZIP nach `config/` und liefert die Pfade fuer Runtime,
  Workdir und Reports zurueck. Bei Default-Temp-Staging wird das Ergebnis in
  der JVM gecacht und beim Prozessende best effort aufgeraeumt.
- `ChildFirstUrlClassLoader`
  laedt KoSIT-nahe Pakete wie `de.kosit.`, `net.sf.saxon.`, `jakarta.xml.bind.`
  und `org.slf4j.` bevorzugt aus dem gestagten Runtime-Verzeichnis, waehrend
  JDK-Pakete parent-first bleiben.

  Skizze: Fuer bekannte Konfliktpakete wird zuerst `findClass` im isolierten
  Loader versucht. JDK-nahe Pakete laufen parent-first, damit keine
  Plattformklassen ueberschattet werden.
- `ReflectiveKositValidator`
  ist die direkte KoSIT-Grenze ohne KoSIT-Imports. Die Klasse laedt
  `ProcessorProvider`, `Configuration`, `DefaultCheck` und `ByteArrayInput` per
  Klassennamen, baut daraus den KoSIT-Check und bildet das KoSIT-`Result` auf
  das JSON-Ergebnis ab. Sie entpackt ausserdem das Konfigurations-ZIP in das
  Arbeitsverzeichnis.

  Skizze: Die Methode `verify` bekommt nur Strings/Pfade aus dem Host. Innerhalb
  des isolierten Context ClassLoaders entpackt sie die Konfiguration, erzeugt
  KoSIT-Objekte per Reflection, schreibt den XML-Report und mappt zentrale
  Result-Felder sowie Meldungen in einfache Maps und Listen.
- `KositRuntimeDiagnostics`
  prueft ausgewaehlte Konfliktklassen und JAXP-Factories und meldet
  ClassLoader, CodeSource und Versionen.

  Skizze: Die Diagnose laedt typische Konfliktklassen ueber den isolierten
  ClassLoader und dokumentiert deren CodeSource. Damit ist sofort sichtbar, ob
  z. B. Saxon wirklich aus dem gestagten Runtime-Verzeichnis kommt.
- `JsonSupport`
  serialisiert das technische Ergebnis mit Jackson auf der Host-Seite.

  Skizze: Der isolierte KoSIT-Teil liefert nur JDK-Maps, Listen und skalare
  Werte. `JsonSupport` macht daraus im Host-Classpath das JSON-Ergebnis; Jackson
  wird deshalb nicht in den isolierten KoSIT-Runtime-Classpath kopiert.

## Build

Vom Repository-Wurzelverzeichnis:

```bash
mvn -f prototypes/kosit-isolated-classloader-verifier/pom.xml package
```

Der Build erzeugt:

- `prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar`
- KoSIT und Runtime-Dependencies unter
  `prototypes/kosit-isolated-classloader-verifier/target/kosit-runtime/lib/`

Das Host-Jar enthaelt Host-Code, Jackson und die KoSIT-Welt als Ressourcen:

- `kosit-isolated/runtime-jars.list`
- `kosit-isolated/runtime-lib/*.jar`
- `kosit-isolated/config/xrechnung-3.0.2-validator-configuration-2026-01-31.zip`

KoSIT, Saxon, JAXB, XMLResolver und SLF4J liegen darin nicht als geshadete
Host-Klassen, sondern als kopierbare Ressourcen fuer den isolierten
ClassLoader.

## Aufruf

Mit einer vorhandenen XML-Datei:

```bash
java -jar prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --diagnostics
```

Mit einer vorher gerenderten Rechnung:

```bash
mvn -f prototypes/velocity-renderer/pom.xml package
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml

java -jar prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar \
  --xml /tmp/invoice-full.xml \
  --report-out /tmp/invoice-full-isolated-report.xml \
  --json-out /tmp/invoice-full-isolated-result.json \
  --diagnostics
```

Nur die isolierte Runtime laden und die Klassenquellen pruefen:

```bash
java -jar prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar \
  --diagnostics-only
```

## Optionen

- `--xml <path>`
  Pflicht fuer Validierungen; XML-Datei, die validiert werden soll.
- `--diagnostics-only`
  Optional; staged bzw. laedt nur die isolierte Runtime und gibt
  ClassLoader-/CodeSource-Diagnosen aus.
- `--config <zip>`
  Optional; schaltet in den expliziten Dateisystem-Modus. Ohne diese Option
  wird das Konfigurations-ZIP aus den Classpath-Ressourcen gestaged.
- `--runtime-lib-dir <path>`
  Optional; schaltet in den expliziten Dateisystem-Modus. Ohne diese Option
  werden die Runtime-Jars aus den Classpath-Ressourcen gestaged.
- `--stage-dir <path>`
  Optional; Zielverzeichnis fuer Classpath-Staging. Ohne diese Option wird ein
  temporaeres Verzeichnis angelegt.
- `--work-dir <path>`
  Optional; Default ist im Classpath-Modus das gestagte `validator-work/`, im
  Dateisystem-Modus `target/validator-work`.
- `--report-out <path>`
  Optional; Default ist im Classpath-Modus das gestagte `reports/`, im
  Dateisystem-Modus `target/reports/<xml-name>-report.xml`.
  Fuer dauerhaft zu inspizierende Reports im Classpath-Modus sollte ein
  expliziter Pfad gesetzt werden, weil temporaeres Default-Staging beim
  Prozessende aufgeraeumt wird.
- `--json-out <path>`
  Optional; schreibt das JSON-Ergebnis zusaetzlich auf Platte.
- `--diagnostics`
  Optional; gibt ClassLoader-, CodeSource- und Versionsdiagnosen aus.

## Ergebnis

Die CLI schreibt zuerst eine Kurzzeile:

- `ACCEPTED`
- `REJECTED`
- `DIAGNOSTICS`
- `ERROR`

Darunter steht ein kompaktes JSON-Ergebnis. Das Format ist in
[`docs/result-format.md`](./docs/result-format.md) beschrieben.

## Bundle-Nutzung

Der Prototyp nutzt das commitete ZIP aus `bundle-docs/` beim Build als
kuratierte gemeinsame Ressource. Zur Laufzeit muss dieses ZIP im Default-Modus
nicht als normale Datei neben der Anwendung liegen: Es steckt im Host-Jar unter
`kosit-isolated/config/`, wird in das Staging-Verzeichnis kopiert und vom
reflektiven KoSIT-Aufruf nach `validator-work/` entpackt.

Fuer lokale Vergleiche kann mit `--config` weiterhin ein ZIP aus dem
Dateisystem uebergeben werden. Dann wird kein Classpath-Staging fuer die
Konfiguration verwendet.

Die aktuelle Extraktion nutzt fuer den Spike einen Marker im entpackten Bundle,
um wiederholte Laeufe schnell zu halten. Fuer Produktion reicht das nicht:
robuster waere ein unveraenderliches Arbeitsverzeichnis pro Bundle-Version und
ZIP-Hash oder eine kleine Manifestdatei, z. B. mit ZIP-Pfad, Groesse,
Zeitstempel, SHA-256 und erwarteter `scenarios.xml`. Bei Abweichungen sollte
neu entpackt oder der Start klar verweigert werden.

## ClassLoader-Diagnose

Die Details stehen in
[`docs/classloader-strategy.md`](./docs/classloader-strategy.md).

Besonders wichtig ist `--diagnostics`: Damit sieht man fuer Konfliktklassen wie
`net.sf.saxon.Version` und `org.slf4j.LoggerFactory`, aus welcher Jar sie
geladen wurden. Im Default-Modus sollte die CodeSource auf ein temporaeres oder
per `--stage-dir` gesetztes Staging-Verzeichnis zeigen, z. B.
`.../runtime-lib/Saxon-HE-12.9.jar`. Im expliziten Dateisystem-Modus zeigt sie
auf das angegebene `--runtime-lib-dir`.

`--diagnostics-only` fuehrt dieselbe Diagnose ohne XML-Validierung aus. Der
Modus ist der schnellste Check, ob Host-Classpath und isolierte KoSIT-Welt
wirklich getrennt sind.

`org.slf4j.` wird in diesem Spike aktuell child-first geladen. Das mitkopierte
`slf4j-simple` ist nur ein Prototypen-Binding; eine produktive
Logging-Integration waere eine eigene Architekturentscheidung.

## Offene Produktionsfragen

- Thread Context ClassLoader muss vor jedem isolierten Aufruf gesetzt und
  danach sicher zurueckgesetzt werden.
- Thread-Safety und Wiederverwendung einer KoSIT-Instanz sind noch nicht
  ausgereizt; der Spike erzeugt pro Aufruf konservativ einen Check.
- Bundle-Updates ohne Neustart brauchen eine klare Reload-Strategie.
- ClassLoader-Caching und kontrolliertes Schliessen muessen zusammen gedacht
  werden.
- Das Staging-Verzeichnis braucht im Betrieb einen lokal schreibbaren,
  knotenlokalen Pfad. Bei mehreren Knoten sollte jeder Knoten sein eigenes
  Staging bekommen; ein gemeinsam beschreibbares Netzlaufwerk waere fuer diese
  Aufgabe unnoetig fehleranfaellig.
- Temp-Cleanup, Restarts und Diagnoseartefakte muessen zusammen geplant werden:
  Jars und entpackte Konfiguration duerfen nicht waehrend laufender
  Validierungen verschwinden, sollen aber nach Versionswechseln nicht
  unbegrenzt liegen bleiben.
- Logging-Integration ist bewusst offen, falls Host und KoSIT-Laufzeit
  unterschiedliche SLF4J-APIs oder Bindings verwenden.
- Austauschbare Runtime- oder Bundle-Verzeichnisse vergroessern die
  Angriffs- und Fehlkonfigurationsflaeche.
- Speicherlecks durch ClassLoader-Retention sind bei Reloads ein reales Risiko.
- Parallele Validierungen brauchen klare Instanz-, Thread- und
  Backpressure-Regeln.
- Fehler- und Timeout-Verhalten ist innerhalb einer JVM schwieriger hart zu
  begrenzen als bei einer Prozessgrenze.
- Betrieb und Diagnose brauchen Metriken fuer Validierungsdauer, Fehlerarten,
  Reloads und ClassLoader-Versionen.

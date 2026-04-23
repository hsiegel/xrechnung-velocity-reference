# KoSIT Isolated ClassLoader Verifier

Dieser Prototyp zeigt, wie ein Host-Java-Programm KoSIT Validator `1.6.2`
innerhalb derselben JVM ausfuehren kann, ohne KoSIT, Saxon, JAXB oder
XMLResolver im normalen Host-Classpath zu haben.

Die KoSIT-Welt wird zur Laufzeit aus `target/kosit-runtime/lib/` in einen
eigenen `URLClassLoader` geladen. Der Host ruft nur ein kleines eigenes
Bridge-Interface auf.

## Zweck

Der Spike ist fuer Host-Umgebungen interessant, in denen der globale Classpath
abweichende XML- oder Logging-Bibliotheken enthalten kann, waehrend KoSIT `1.6.2`
moderne Dependencies wie Saxon-HE `12.9`, JAXB `4.x` und SLF4J `2.x` braucht.

Der Nachweis ist bewusst eng:

- eine JVM
- kein Kindprozess
- kein HTTP
- kein MCP
- Host-Code ohne KoSIT-Compile-Dependency
- KoSIT-Code nur im isolierten Adapter

## Was dieser Spike beweist / was er nicht beweist

Der Spike beweist:

- Das Host-Jar startet ohne KoSIT, Saxon, JAXB, XMLResolver oder SLF4J im
  normalen Host-Classpath.
- Die Bridge-Implementierung wird reflektiv aus `target/kosit-runtime/lib/`
  geladen.
- KoSIT-nahe Konfliktklassen wie `net.sf.saxon.Version`,
  `jakarta.xml.bind.JAXBContext` und `org.slf4j.LoggerFactory` kommen aus dem
  isolierten Runtime-Verzeichnis.
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

## Unterschied zum Embedded Verifier

[`../kosit-embedded-verifier/`](../kosit-embedded-verifier/) linkt direkt gegen
KoSIT und hat KoSIT/Saxon/JAXB im normalen Maven- und Runtime-Classpath dieses
Prototyps.

Dieser Prototyp trennt dagegen:

- `host/`
  baut das ausfuehrbare CLI-Jar und kennt nur JDK plus Bridge.
- `bridge/`
  enthaelt `VerifierBridge` als kleine eigene Grenze zwischen Host und Adapter.
- `isolated-adapter/`
  kompiliert gegen KoSIT `1.6.2` und wird erst zur Laufzeit vom Host geladen.

## Build

Vom Repository-Wurzelverzeichnis:

```bash
mvn -f prototypes/kosit-isolated-classloader-verifier/pom.xml package
```

Der Build erzeugt:

- `prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar`
- `prototypes/kosit-isolated-classloader-verifier/target/kosit-runtime/lib/kosit-isolated-adapter.jar`
- KoSIT und Runtime-Dependencies unter
  `prototypes/kosit-isolated-classloader-verifier/target/kosit-runtime/lib/`

Das Host-Modul haengt dabei nur vom lokalen `bridge`-Modul ab. KoSIT, Saxon,
JAXB, XMLResolver und SLF4J stehen ausschliesslich am isolierten Adapter und im
kopierten Runtime-Verzeichnis.

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
  Optional; laedt nur `target/kosit-runtime/lib/`, instanziiert den isolierten
  Adapter und gibt ClassLoader-/CodeSource-Diagnosen aus.
- `--config <zip>`
  Optional; Default ist das passende
  `bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`.
- `--runtime-lib-dir <path>`
  Optional; Default ist `target/kosit-runtime/lib`.
- `--work-dir <path>`
  Optional; Default ist `target/validator-work`.
- `--report-out <path>`
  Optional; Default ist `target/reports/<xml-name>-report.xml`.
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

Der Prototyp kopiert keine Inhalte aus `bundle-docs/` ins Modul. Stattdessen
findet der Host das commitete ZIP
`bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`, uebergibt den Pfad
an die isolierte Bridge und der Adapter entpackt das ZIP bei Bedarf nach
`target/validator-work/`.

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
geladen wurden. Fuer Saxon sollte die CodeSource auf
`target/kosit-runtime/lib/Saxon-HE-12.9.jar` zeigen.

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

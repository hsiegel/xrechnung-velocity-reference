# XRechnung Reference Kit

Dieses Repository sammelt kleine Werkzeuge, Referenzdateien und Beispiele fuer
`XRechnung 3.0.2` als `UBL Invoice`. Der Fokus ist inzwischen breiter als nur
Template-Rendering: Das Repo vergleicht mehrere Wege, Rechnungen aus einem
semantischen Modell zu erzeugen, vorhandene XML-Rechnungen zu validieren und
KoSIT in unterschiedlichen Integrationsformen nutzbar zu machen.

Die Prototypen beantworten drei praktische Fragen:

- Wie erzeugen wir reproduzierbares UBL-XML aus einem gemeinsamen
  Rechnungsmodell?
- Wie pruefen wir vorhandene XRechnungs-XMLs lokal und nachvollziehbar?
- Welche Integrationsgrenzen sind fuer produktionsnahe Verifier sinnvoll:
  direkter Library-Aufruf, isolierter ClassLoader, lokaler MCP-Service,
  MCP-Client oder bewusst KoSIT-freier Saxon-Eigenbau?

Aktuell gibt es hier acht praktische Prototypen:

- einen Velocity-Renderer fuer `UBL Invoice`
- einen KoSIT-Embedded-Verifier fuer vorhandene XML-Dateien
- einen KoSIT-Isolated-ClassLoader-Verifier fuer Same-VM-Isolation
- einen lokalen KoSIT-MCP-Service fuer Codex und andere Hosts
- einen Java-Verifier, der diesen MCP-Service als Kindprozess nutzt
- einen Java-Verifier, der lazy einen lokalen HTTP-Worker als Kindprozess nutzt
- einen Saxon-Output-Verifier-Spike ohne KoSIT-Laufzeit-Engine
- einen Haskell-Verifier als kleine Library plus CLI

## Bausteine

- Das gemeinsame semantische Rechnungsmodell liegt unter
  [`semantic-model/`](./semantic-model/).
- Ausgefuellte Beispiele dazu liegen unter [`examples/`](./examples/).
- Die Rendering-Seite liefert Velocity-Templates, ein lokales Java-Harness und
  einen dokumentierten XML-Helper fuer saubere UBL-XML-Ausgabe.
- Die Verifier-Seite liefert mehrere Java-Referenzpfade: KoSIT embedded,
  KoSIT in einem isolierten Same-VM-ClassLoader, KoSIT als lokaler MCP-Service,
  ein Java-Client ueber MCP, ein Java-Client ueber einen lokalen HTTP-Worker und
  einen Saxon-basierten Spike ohne KoSIT-Laufzeit-Engine.
- Die Haskell-Seite liefert eine kleine Bibliothek und die CLI
  `xrechnung-haskell-verifier` fuer Modell-, XML- und Verifier-Experimente in
  einer zweiten Sprache.
- `bundle-docs/` ist die gemeinsame kuratierte Referenzbasis fuer Validator-
  Konfiguration, Testsuite und Standardunterlagen; Prototypen nutzen diese
  Artefakte direkt, statt sie zu kopieren.

## Verzeichnisstruktur

- `bundle-docs/`
  Kuratierte Referenzquellen aus dem offiziellen XRechnung-Bundle.
- `templates/`
  Eigenstaendige Single-File-Velocity-Templates fuer `UBL Invoice` plus
  Template-Pattern- und Helper-Doku.
- `semantic-model/`
  Gemeinsames semantisches Rechnungsmodell, Vertragsbeschreibung und
  Mapping-Hinweise, XRechnung-Strukturuebersichten und JSON Schema.
- `mapping/`
  Mapping-Vorlage fuer externe Datenquellen auf das semantische Rechnungsmodell.
- `examples/`
  Ausgefuellte Beispielinstanzen fuer `core` und `full`.
- `prototypes/`
  Sammelordner fuer die Referenzimplementierungen und Validierungs-Spikes.
- `prototypes/velocity-renderer/`
  Kleines lokales Java-Harness fuer echte Render- und Validatorlaeufe.
- `prototypes/kosit-embedded-verifier/`
  Kleiner lokaler Java-Prototyp fuer eingebettete KoSIT-Validierung gegen
  vorhandene XML-Dateien.
- `prototypes/kosit-isolated-classloader-verifier/`
  Same-VM-Prototyp, der KoSIT `1.6.2` ueber einen isolierten URLClassLoader
  laedt, ohne KoSIT/Saxon/JAXB in den Host-Classpath zu legen.
- `prototypes/saxon-output-verifier-spike/`
  Bewusst kleiner Java-Verifier-Kern fuer einen moeglichen Saxon-HE-
  Eigenbau; Architektur, Risiken und Update-Folgen stehen in der README.
- `prototypes/kosit-verification-mcp-service/`
  Lokaler STDIO-MCP-Service mit embedded KoSIT-Validierung und
  maschinenlesbarem JSON-Ergebnisformat.
- `prototypes/kosit-via-mcp-verifier/`
  Java-Client/Host-Prototyp, der den MCP-Service als Kindprozess startet und
  `xrechnung_validate` ueber STDIO/MCP aufruft.
- `prototypes/kosit-via-http-verifier/`
  Java-Client/Host-Prototyp, der lazy einen lokalen HTTP-Worker als
  Kindprozess startet und Rechnungs-XML per localhost-HTTP validieren laesst.
- `prototypes/haskell-verifier/`
  Kleine Haskell-Library und CLI fuer `XRechnung`-Modelle, UBL-XML und
  Validation.

## Prototypen-Matrix

| Prototyp | Funktion | Engine / Ansatz | Input | Ergebnis / Status |
|---|---|---|---|---|
| [`velocity-renderer`](./prototypes/velocity-renderer/) | rendert die Velocity-Templates und kann optional direkt validieren | Velocity 1.6.4 plus KoSIT-Standalone-JAR fuer `--validate` | semantisches YAML/JSON-Modell | UBL-XML, optional KoSIT-XML-/HTML-Reports; Referenzpfad fuer Template-Smokes |
| [`kosit-embedded-verifier`](./prototypes/kosit-embedded-verifier/) | validiert vorhandene Rechnungs-XML | embedded KoSIT-Validator-API | XML-Datei | Konsolenergebnis und XML-Report; klein gehaltener embedded Vergleichspfad |
| [`kosit-isolated-classloader-verifier`](./prototypes/kosit-isolated-classloader-verifier/) | validiert in derselben JVM mit isolierter KoSIT-Welt | Host-Jar plus isolierter URLClassLoader fuer KoSIT `1.6.2` und moderne Dependencies | XML-Datei | `ACCEPTED`/`REJECTED`/`ERROR`, XML-Report, JSON-Ergebnis und optionale ClassLoader-Diagnose |
| [`kosit-verification-mcp-service`](./prototypes/kosit-verification-mcp-service/) | stellt KoSIT-Validierung lokal per MCP bereit | STDIO-MCP-Service mit wiederverwendeter embedded KoSIT-Instanz | `xmlPath` oder `xmlContent` | strukturiertes JSON, optional `input.xml`, `result.json`, `report.xml` als Run-Artefakte |
| [`kosit-via-mcp-verifier`](./prototypes/kosit-via-mcp-verifier/) | validiert ueber den lokalen MCP-Service statt direkt ueber KoSIT | Java-MCP-Client startet Service als Kindprozess | XML-Datei oder Inline-XML | `ACCEPTED`/`REJECTED`/`TECHNICAL_FAILURE` plus gemapptes JSON |
| [`kosit-via-http-verifier`](./prototypes/kosit-via-http-verifier/) | validiert ueber einen lokalen HTTP-Worker statt direkt ueber KoSIT | Java-Client startet lazy ein Worker-Kindprozess-JAR auf `127.0.0.1` | XML-Datei oder Inline-XML | `ACCEPTED`/`REJECTED`/`TECHNICAL_FAILURE` plus gemapptes JSON |
| [`saxon-output-verifier-spike`](./prototypes/saxon-output-verifier-spike/) | prueft den erwarteten Ausgangsrechnungstyp profilgesteuert | JAXP-XSD plus Saxon-HE fuer vorkompilierte Schematron-XSLTs | XML-Datei und optional `--profile` | `PASS`/`FAIL`/`ERROR`, JSON-Ergebnis und SVRL; bewusst kein KoSIT-Paritaetsversprechen |
| [`haskell-verifier`](./prototypes/haskell-verifier/) | modelliert, liest, rendert und prueft `UBL Invoice` in Haskell | Haskell-Library und Stack-CLI | XML oder semantisches YAML | Validation-Issues, normiertes XML, Modellansicht oder XML-Ausgabe |

## Verifier-Landkarte

Die Verifier-Prototypen sind bewusst keine konkurrierenden Umbauten desselben
Codes, sondern vergleichen unterschiedliche Integrationsgrenzen:

- `velocity-renderer --validate` ist der schnelle Smoke-Pfad fuer gerenderte
  Templates und nutzt das KoSIT-Standalone-JAR.
- `kosit-embedded-verifier` ist der kleinste direkte Java-Vergleichspfad ueber
  die embedded KoSIT-API.
- `kosit-isolated-classloader-verifier` prueft, ob KoSIT in derselben JVM mit
  eigenem Runtime-Classpath laufen kann.
- `kosit-verification-mcp-service` kapselt KoSIT als lokalen STDIO-MCP-Service
  mit maschinenlesbarem Ergebnisformat.
- `kosit-via-mcp-verifier` zeigt die passende Java-Client-Seite, die den MCP-
  Service wie eine lokale Integrationsgrenze nutzt.
- `kosit-via-http-verifier` zeigt dieselbe Grundidee mit einer lokalen
  HTTP-Prozessgrenze, inklusive lazy Worker-Start.
- `saxon-output-verifier-spike` erkundet den KoSIT-freien Weg und dokumentiert
  bewusst die Kosten und offenen Punkte eines Eigenbaus.
- `haskell-verifier` ist ein zweiter Sprachpfad fuer Modell-, XML- und
  Verifier-Experimente.

## Ausgewaehlte Kern-Dateien

- `templates/ubl-invoice-full.vm`
  Referenz-Template fuer `XRechnung 3.0.2 / UBL Invoice`.
- `semantic-model/ubl-invoice-full-stub.yaml`
  Null-initialisierte Vollvorlage fuer das semantische Rechnungsmodell.
- `semantic-model/ubl-invoice-semantic-model-contract.md`
  Kurzvertrag fuer Form und Semantik des Rechnungsmodells.
- `semantic-model/ubl-invoice-core-mapping.yaml`
  Mapping- und Render-Konventionen fuer den UBL-Schnitt.
- `semantic-model/xrechnung.schema.json`
  Maschinenlesbares JSON Schema fuer YAML/JSON im semantischen Modellformat.
- `examples/ubl-invoice-full-example.yaml`
  Vollere Beispielinstanz des Rechnungsmodells.
- `templates/xml-helper-contract.md`
  Leitfaden fuer die `$xml`-Helper-Funktionen und fuer eine saubere,
  null-tolerante XML-Ausgabe mit Velocity.
- `prototypes/velocity-renderer/src/main/java/local/xrechnung/velocityrenderer/XmlHelper.java`
  Referenzimplementierung der XML-Helfer fuer das Velocity-Rendering.
- `prototypes/velocity-renderer/src/main/java/local/xrechnung/velocityrenderer/VelocityRendererCli.java`
  Minimales Testprogramm fuer echte Renderlaeufe.
- `prototypes/kosit-embedded-verifier/README.md`
  Direkter embedded KoSIT-Verifier fuer vorhandene XML-Dateien.
- `prototypes/saxon-output-verifier-spike/README.md`
  Architektur, Implementierungsfluss und Risiko-/Maintenance-Hinweise fuer den
  Saxon-basierten Eigenbau.
- `prototypes/saxon-output-verifier-spike/docs/result-format.md`
  Maschinenlesbares Ergebnisformat des Saxon-Output-Verifier-Spikes.
- `prototypes/kosit-isolated-classloader-verifier/README.md`
  Einstieg in die Same-VM-Isolation von KoSIT ueber einen eigenen
  URLClassLoader.
- `prototypes/kosit-isolated-classloader-verifier/docs/classloader-strategy.md`
  Details zu parent-first/child-first-Regeln, SLF4J und Diagnoseklassen.
- `prototypes/kosit-verification-mcp-service/docs/result-format.md`
  Maschinenlesbares Ergebnisformat des lokalen KoSIT-MCP-Services.
- `prototypes/kosit-via-mcp-verifier/README.md`
  Java-Client-Prototyp fuer Validierung ueber den lokalen MCP-Service.
- `prototypes/kosit-via-mcp-verifier/docs/production-considerations.md`
  Betriebs- und Integrationsfragen fuer den Java-Client ueber MCP.
- `prototypes/kosit-via-http-verifier/README.md`
  Java-Client-Prototyp fuer Validierung ueber einen lazy lokalen HTTP-Worker.
- `prototypes/kosit-via-http-verifier/docs/production-considerations.md`
  Betriebs- und Integrationsfragen fuer den HTTP-Worker neben einer lang
  laufenden Host-Anwendung.
- `prototypes/haskell-verifier/README.md`
  Einstieg in Library, CLI und YAML/XML-Workflows der Haskell-Tools.

## Schnellstart

### Velocity Renderer

```bash
mvn -f prototypes/velocity-renderer/pom.xml package
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml
```

Mit Validatorlauf:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml \
  --validate
```

Der Renderer setzt insbesondere:

- `runtime.references.strict = false`
- Inline-Makros erlaubt
- fuer lokale Validierung den KoSIT-Validator `1.6.2` aus dem Maven-Build und
  die versionierte XRechnung-Konfiguration unter
  `bundle-docs/`

Der XML-Helper ist dabei ein zentraler Baustein: Er beschreibt und implementiert
die kleine API, ueber die die Templates Presence-Checks, XML-Escaping und
Zahlen-/Datumsformatierung konsistent abwickeln.

Die Beispielmodelle unter [`examples/`](./examples/) sind dabei die
kanonische Sample-Quelle fuer lokale Render- und Validierungslaufe.
Sie verweisen direkt auf das JSON Schema unter
[`semantic-model/xrechnung.schema.json`](./semantic-model/xrechnung.schema.json).

Fuer einen fruehen Strukturcheck reicht auch:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --model examples/ubl-invoice-full-example.yaml \
  --check-model
```

Normale Renderlaeufe machen diesen Strukturcheck inzwischen automatisch
mit; `--check-model` ist der schnelle Einzelaufruf dafuer.

### KoSIT Embedded Verifier

```bash
mvn -f prototypes/kosit-embedded-verifier/pom.xml package
java -jar prototypes/kosit-embedded-verifier/target/kosit-embedded-verifier.jar \
  --xml /tmp/invoice-full.xml
```

Der Embedded-Verifier nutzt dieselbe versionierte
`bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`, entpackt sie bei
Bedarf nach `prototypes/kosit-embedded-verifier/target/validator-work/` und
validiert die
XML ueber die eingebettete KoSIT-API.

Im Unterschied zur optionalen Validierung im Velocity Renderer schreibt dieser
Prototyp bewusst nur einen XML-Report. Details dazu stehen in
[`prototypes/kosit-embedded-verifier/README.md`](./prototypes/kosit-embedded-verifier/README.md).

### KoSIT Isolated ClassLoader Verifier

```bash
mvn -f prototypes/kosit-isolated-classloader-verifier/pom.xml package
java -jar prototypes/kosit-isolated-classloader-verifier/target/kosit-isolated-classloader-verifier.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --diagnostics
```

Dieser Prototyp laedt KoSIT `1.6.2` samt Saxon, JAXB, XMLResolver und SLF4J
zur Laufzeit aus `prototypes/kosit-isolated-classloader-verifier/target/kosit-runtime/lib/`.
Das Host-Jar selbst enthaelt nur Host-Code und die kleine Bridge-Schnittstelle.
Mit `--diagnostics` wird sichtbar, aus welchen Jars Konfliktklassen wie
`net.sf.saxon.Version` und `org.slf4j.LoggerFactory` geladen wurden.

Details stehen in
[`prototypes/kosit-isolated-classloader-verifier/README.md`](./prototypes/kosit-isolated-classloader-verifier/README.md).

### Saxon Output Verifier Spike

```bash
mvn -f prototypes/saxon-output-verifier-spike/pom.xml package
java -jar prototypes/saxon-output-verifier-spike/target/saxon-output-verifier-spike.jar \
  --xml /tmp/invoice-full.xml \
  --profile xrechnung-ubl-invoice
```

Der Spike zeigt den geplanten Ablauf fuer einen moeglichen Saxon-HE-Eigenbau:
Bundle-Lokalisierung, lokales Entpacken, profilgesteuertes Scope-Gate,
JAXP-XSD-Validierung, rohe Schematron-SVRL-Laeufe und ein kleines JSON-
Ergebnis ohne KoSIT-Engine.

Er erzeugt bewusst noch keinen KoSIT-nahen Accept-/Reject-Befund. Architektur,
offene Grenzen und Update-Folgen stehen in
[`prototypes/saxon-output-verifier-spike/README.md`](./prototypes/saxon-output-verifier-spike/README.md).

### KoSIT Verification MCP Service

```bash
mvn -f prototypes/kosit-verification-mcp-service/pom.xml package
java -jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar
```

Der MCP-Server ist fuer lokale Codex-Nutzung gedacht, validiert ueber die
embedded KoSIT-API und kann bei Bedarf `input.xml`, `result.json` und
`report.xml` unter `prototypes/kosit-verification-mcp-service/target/runs/<run-id>/`
ablegen.

Die lokale Codex-Einbindung und das Ergebnisformat sind in
[`prototypes/kosit-verification-mcp-service/README.md`](./prototypes/kosit-verification-mcp-service/README.md) und
[`prototypes/kosit-verification-mcp-service/docs/result-format.md`](./prototypes/kosit-verification-mcp-service/docs/result-format.md)
dokumentiert.

### KoSIT Via MCP Verifier

```bash
mvn -f prototypes/kosit-via-mcp-verifier/pom.xml package
java -jar prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar \
  --service-jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --persist-artifacts false
```

Dieser Prototyp ist die Java-Client-/Host-Seite: Er startet den MCP-Service als
Kindprozess, entdeckt `xrechnung_validate`, ruft das Tool auf und mappt das
`structuredContent` in ein eigenes `VerificationResult`. Er ist ein
Referenzpfad fuer eine kontrollierte lokale MCP-Service-Integration, aber keine
fertige Produktionsarchitektur.

### KoSIT Via HTTP Verifier

```bash
mvn -f prototypes/kosit-via-http-verifier/pom.xml package
java -jar prototypes/kosit-via-http-verifier/target/kosit-via-http-verifier.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --persist-artifacts false
```

Dieser Prototyp ist ebenfalls eine Java-Client-/Host-Seite, startet aber beim
ersten Zugriff lazy einen lokalen HTTP-Worker als Kindprozess. Der Worker bindet
auf `127.0.0.1` mit OS-gewaehltem Port, validiert seriell ueber embedded KoSIT
und liefert JSON an den Host zurueck.

### Haskell Verifier

```bash
cd prototypes/haskell-verifier
stack build
stack run xrechnung-haskell-verifier -- verify /pfad/zur/rechnung.xml
```

Weitere typische Aufrufe:

```bash
stack run xrechnung-haskell-verifier -- format /pfad/zur/rechnung.xml
stack run xrechnung-haskell-verifier -- pretty-model /pfad/zur/rechnung.xml
stack run xrechnung-haskell-verifier -- to-xml ../../examples/ubl-invoice-core-example.yaml
```

Mehr Details dazu stehen in
[`prototypes/haskell-verifier/README.md`](./prototypes/haskell-verifier/README.md).

## Scope

Diese Sammlung deckt aktuell `XRechnung 3.0.2` fuer `UBL Invoice` ab.
`CII` und `CreditNote` sind derzeit nicht Teil dieses Repos.

## License

Der eigene Quell- und Dokumentationsanteil dieses Repos steht unter
[0BSD](./LICENSE.md).

Die unter `bundle-docs/` abgelegten Referenzartefakte stammen aus externen
offiziellen Bundles und behalten ihre jeweiligen Original-Lizenz- und
Nutzungsbedingungen.

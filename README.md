# XRechnung Reference Kit

Dieses Repository sammelt kleine Werkzeuge, Referenzdateien und Beispiele fuer
`XRechnung 3.0.2` als `UBL Invoice`.

Aktuell gibt es hier zwei praktische Schienen:

- Velocity-Templates zum Rendern von `UBL Invoice`
- eine kleine Haskell-Library plus CLI zum Parsen, Rendern, Validieren und
  Konvertieren

## Bausteine

- Das gemeinsame semantische Rechnungsmodell liegt unter
  [`semantic-model/`](./semantic-model/).
- Ausgefuellte Beispiele dazu liegen unter [`examples/`](./examples/).
- Die Velocity-Seite liefert einsatzfertige Templates, ein lokales Java-Harness
  und einen dokumentierten XML-Helper fuer sauberes Velocity-Rendering.
- Die Haskell-Seite liefert eine kleine Bibliothek und die CLI
  `xrechnung-cli`.

## Verzeichnisstruktur

- `bundle-docs/`
  Kuratierte Referenzquellen aus dem offiziellen XRechnung-Bundle.
- `templates/`
  Eigenstaendige Single-File-Velocity-Templates fuer `UBL Invoice`.
- `semantic-model/`
  Gemeinsames semantisches Rechnungsmodell, Vertragsbeschreibung und
  Mapping-Hinweise plus JSON Schema.
- `mapping-matrix/`
  Mapping-Vorlage fuer externe Datenquellen auf das Rechnungsmodell.
- `examples/`
  Ausgefuellte Beispielinstanzen fuer `core` und `full`.
- `haskell-tools/`
  Kleine Haskell-Library und CLI fuer `XRechnung`-Modelle, UBL-XML und
  Validation.
- `notes/`
  Arbeitsnotizen, Strukturuebersichten und Helper-Vertrag.
- `velocity-runner/`
  Kleines lokales Java-Harness fuer echte Render- und Validatorlaeufe.

## Kern-Dateien

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
- `notes/xml-helper-contract.md`
  Leitfaden fuer die `$xml`-Helper-Funktionen und fuer eine saubere,
  null-tolerante XML-Ausgabe mit Velocity.
- `velocity-runner/src/main/java/local/xrechnung/velocityrunner/XmlHelper.java`
  Referenzimplementierung der XML-Helfer fuer das Velocity-Rendering.
- `velocity-runner/src/main/java/local/xrechnung/velocityrunner/VelocitySmokeRenderer.java`
  Minimales Testprogramm fuer echte Renderlaeufe.
- `haskell-tools/README.md`
  Einstieg in Library, CLI und YAML/XML-Workflows der Haskell-Tools.

## Schnellstart

### Velocity

```bash
mvn -f velocity-runner/pom.xml package
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml
```

Mit Validatorlauf:

```bash
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml \
  --validate
```

Der Runner setzt insbesondere:

- `runtime.references.strict = false`
- Inline-Makros erlaubt
- fuer lokale Validierung den KoSIT-Validator `1.6.0` aus dem Maven-Build und
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
java -jar velocity-runner/target/velocity-runner.jar \
  --model examples/ubl-invoice-full-example.yaml \
  --check-model
```

Normale Renderlaeufe machen diesen Strukturcheck inzwischen automatisch
mit; `--check-model` ist der schnelle Standalone-Weg dafuer.

### Haskell

```bash
cd haskell-tools
stack build
stack run -- verify /pfad/zur/rechnung.xml
```

Weitere typische Aufrufe:

```bash
stack run -- format /pfad/zur/rechnung.xml
stack run -- pretty-model /pfad/zur/rechnung.xml
stack run -- to-xml ../examples/ubl-invoice-core-example.yaml
```

Mehr Details dazu stehen in
[`haskell-tools/README.md`](./haskell-tools/README.md).

## Scope

Diese Sammlung deckt aktuell `XRechnung 3.0.2` fuer `UBL Invoice` ab.
`CII` und `CreditNote` sind derzeit nicht Teil dieses Repos.

## License

Der eigene Quell- und Dokumentationsanteil dieses Repos steht unter
[0BSD](./LICENSE.md).

Die unter `bundle-docs/` abgelegten Referenzartefakte stammen aus externen
offiziellen Bundles und behalten ihre jeweiligen Original-Lizenz- und
Nutzungsbedingungen.

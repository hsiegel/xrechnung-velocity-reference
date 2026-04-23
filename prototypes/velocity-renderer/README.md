# Velocity Renderer

Lokales Test-Harness fuer die UBL-`Invoice`-Templates unter `templates/`.

## Aufgabe

- `Velocity 1.6.4` mit genau den fuer unsere Templates relevanten Einstellungen starten
- ein Rechnungsmodell aus YAML oder JSON plus `$xml` in den Kontext legen
- ein `.vm` aus `templates/` direkt rendern
- das Ergebnis optional mit dem KoSIT-Validator 1.6.2 gegen die lokale
  XRechnung-Validator-Konfiguration pruefen
- den Validator 1.6.2 aus dem Maven-Build und die versionierte
  XRechnung-Konfiguration aus `bundle-docs/` verwenden

## Build

Vom Projektwurzelverzeichnis aus:

```bash
mvn -f prototypes/velocity-renderer/pom.xml package
```

Das erzeugt ein lauffaehiges Fat-JAR:

```bash
prototypes/velocity-renderer/target/velocity-renderer.jar
```

## Start

Vollsicht rendern:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml
```

Core rendern:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-core.vm \
  --model examples/ubl-invoice-core-example.yaml \
  --out /tmp/invoice-core.xml
```

Ohne `--out` wird nach `stdout` geschrieben.

`--model` ist verpflichtend. Erlaubt sind YAML- oder JSON-Dateien mit einem
Top-Level-Objekt `xr`.
Die Dateien unter `examples/` sind dabei die gepflegte Sample-Quelle fuer
lokale Render- und Validierungslaufe.
Normale Renderlaeufe pruefen das Modell vorher automatisch gegen das JSON
Schema unter `semantic-model/xrechnung.schema.json`.

Nur das semantische Modell gegen das JSON Schema pruefen:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --model examples/ubl-invoice-full-example.yaml \
  --check-model
```

Rendern und validieren:

```bash
java -jar prototypes/velocity-renderer/target/velocity-renderer.jar \
  --template templates/ubl-invoice-full.vm \
  --model examples/ubl-invoice-full-example.yaml \
  --out /tmp/invoice-full.xml \
  --validate
```

Dabei nutzt der Renderer automatisch die versionierte Konfiguration unter
`bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`, entpackt sie
nach `prototypes/velocity-renderer/target/validator-work/`, verwendet den per Maven
bereitgestellten KoSIT-Validator `1.6.2` aus
`prototypes/velocity-renderer/target/validator-bin/` und schreibt die Validator-Reports
neben die ausgegebene XML-Datei.

`--check-model` validiert YAML oder JSON gegen
`semantic-model/xrechnung.schema.json` und beendet den Lauf danach mit einem
sauberen Exit-Code.
Mit `--no-model-check` laesst sich der automatische Vorab-Check fuer einen
Renderlauf bei Bedarf explizit abschalten.

## Velocity-Einstellungen

Das Harness setzt:

- `velocimacro.permissions.allow.inline = true`
- `velocimacro.permissions.allow.inline.local.scope = true`
- `velocimacro.context.localscope = true`
- `runtime.references.strict = false`

Damit passen Engine-Verhalten und die null-toleranten Single-File-Templates
zusammen.

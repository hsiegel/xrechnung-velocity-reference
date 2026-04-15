# Velocity Runner

Lokales Test-Harness fuer die UBL-`Invoice`-Templates unter `templates/`.

## Aufgabe

- `Velocity 1.6.4` mit genau den fuer unsere Templates relevanten Einstellungen starten
- ein passendes oeffentliches Beispiel-`$xr` plus `$xrh` in den Kontext legen
- ein `.vm` aus `templates/` direkt rendern
- das Ergebnis optional mit dem KoSIT-Validator 1.6.0 gegen die lokale
  XRechnung-Validator-Konfiguration pruefen
- den Validator 1.6.0 auf aktuellen JVMs direkt aus dem lokalen Bundle nutzen

## Build

Vom Projektwurzelverzeichnis aus:

```bash
mvn -f velocity-runner/pom.xml package
```

Das erzeugt ein lauffaehiges Fat-JAR:

```bash
velocity-runner/target/velocity-runner.jar
```

## Start

Vollsicht rendern:

```bash
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --out /tmp/invoice-full.xml
```

Core rendern:

```bash
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-core.vm \
  --out /tmp/invoice-core.xml
```

Ohne `--out` wird nach `stdout` geschrieben.

Das Harness waehlt das Beispielmodell passend zum Template:

- `templates/ubl-invoice-full.vm` nutzt `SamplePublicInvoiceFactory.fullInvoice()`
- `templates/ubl-invoice-core.vm` nutzt `SamplePublicInvoiceFactory.coreInvoice()`

Rendern und validieren:

```bash
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --out /tmp/invoice-full.xml \
  --validate
```

Dabei nutzt der Runner automatisch das lokale Bundle unter
`bundle-docs/xrechnung-3.0.2-bundle-*.zip`, extrahiert daraus den
`validator-1.6.0` und die XRechnung-Validator-Konfiguration in
`velocity-runner/target/validator-cache/` und schreibt die Validator-Reports
neben die ausgegebene XML-Datei.

## Velocity-Einstellungen

Das Harness setzt:

- `velocimacro.permissions.allow.inline = true`
- `velocimacro.permissions.allow.inline.local.scope = true`
- `velocimacro.context.localscope = true`
- `runtime.references.strict = false`

Damit passen Engine-Verhalten und die null-toleranten Single-File-Templates
zusammen.

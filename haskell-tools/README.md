# Haskell Tools

`haskell-tools/` ist eine kleine Haskell-Library plus CLI fuer
`XRechnung 3.0.2 / UBL Invoice`.

Der Fokus liegt auf drei Dingen:

- semantisches `XRechnung`-Modell als Haskell-Datentypen
- UBL-XML lesen, validieren und wieder rendern
- YAML im Rechnungsmodell-Format aus [`../examples/`](../examples/) direkt nach XML
  umsetzen

## Library

Die wichtigsten Module sind:

- `XRechnung`
  Kleine Fassade fuer die haeufigen Typen und Funktionen.
- `XRechnung.Model`
  Algebraische Datentypen fuer das Modell.
- `XRechnung.Validation`
  Strukturierte Validation-Issues und Rendering davon.
- `XRechnung.Verify`
  Fachliche Validierung auf dem Modell.
- `XRechnung.Ubl.Parse`
  Parser von `UBL Invoice XML` nach `XRechnung`.
- `XRechnung.Ubl.Render`
  Renderer von `XRechnung` nach `UBL Invoice XML`.
- `XRechnung.SemanticModel.Yaml`
  Reader fuer die semantische YAML-Form unter `xr:`.

## CLI

Die CLI heisst `xrechnung-cli` und wird ueber Stack gestartet:

```bash
cd haskell-tools
stack run -- verify /pfad/zur/rechnung.xml
```

Verfuegbare Subcommands:

- `verify FILE`
  Liest `UBL Invoice XML`, parsed es und gibt Validation-Issues aus.
- `format FILE`
  Liest XML und schreibt normiertes UBL-XML nach `stdout`.
- `pretty-model FILE`
  Liest XML und zeigt das geparste `XRechnung`-Modell lesbar an.
- `to-xml FILE`
  Liest YAML im Rechnungsmodell-Format und schreibt UBL-XML nach `stdout`.

`FILE` kann auch `-` sein, um von `stdin` zu lesen.

## Typische Aufrufe

YAML-Beispiel in XML umwandeln:

```bash
cd haskell-tools
stack run -- to-xml ../examples/ubl-invoice-core-example.yaml
```

XML pruefen:

```bash
cd haskell-tools
stack run -- verify /tmp/invoice.xml
```

XML normalisieren:

```bash
cd haskell-tools
stack run -- format /tmp/invoice.xml
```

## Build

```bash
cd haskell-tools
stack build
```

Das Projekt ist absichtlich klein gehalten: eine Library, eine CLI und keine
zusaetzliche Service-Schicht darum herum.

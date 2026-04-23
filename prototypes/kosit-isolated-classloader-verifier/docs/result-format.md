# Result Format

Der Prototyp gibt ein kleines JSON-Objekt aus. Es ist bewusst kein
domain-schoenes Fachmodell, sondern ein technischer Nachweis, dass KoSIT in
einem isolierten ClassLoader laeuft und dennoch ein maschinenlesbares Ergebnis
zurueckkommt.

```json
{
  "status": "ACCEPTED",
  "xmlPath": "/tmp/invoice-full.xml",
  "configPath": "bundle-docs/xrechnung-3.0.2-validator-configuration-2026-01-31.zip",
  "workDir": "prototypes/kosit-isolated-classloader-verifier/target/validator-work",
  "configurationDirectory": "prototypes/kosit-isolated-classloader-verifier/target/validator-work/xrechnung-3.0.2-validator-configuration-2026-01-31",
  "reportPath": "prototypes/kosit-isolated-classloader-verifier/target/reports/invoice-full-report.xml",
  "acceptRecommendation": "ACCEPTABLE",
  "processingSuccessful": true,
  "wellformed": true,
  "schemaValid": true,
  "schematronValid": true,
  "messages": [],
  "diagnostics": {
    "hostClassLoader": "...",
    "isolatedClassLoader": "...",
    "runtimeLibDir": "prototypes/kosit-isolated-classloader-verifier/target/kosit-runtime/lib",
    "threadContextClassLoader": "...",
    "diagnosticsClassLoader": "...",
    "jaxpFactories": [],
    "classes": []
  }
}
```

## Felder

- `status`
  `ACCEPTED`, `REJECTED`, `DIAGNOSTICS` oder `ERROR`.
- `xmlPath`
  Die validierte XML-Datei.
- `configPath`
  Das verwendete XRechnung-Validator-Konfigurations-ZIP.
- `workDir`
  Arbeitsverzeichnis, in das das ZIP entpackt wird.
- `configurationDirectory`
  Entpacktes Konfigurationsverzeichnis.
- `reportPath`
  Geschriebener XML-Report, falls KoSIT einen Report geliefert hat.
- `acceptRecommendation`
  Direkter KoSIT-Wert, z. B. `ACCEPTABLE` oder `REJECTED`.
- `processingSuccessful`
  Technischer KoSIT-Verarbeitungserfolg, nicht fachliche Gueltigkeit.
- `wellformed`, `schemaValid`, `schematronValid`
  KoSIT-Basisindikatoren aus `Result`.
- `messages`
  Rohe technische Meldungen aus Processing Errors, XSD-Verletzungen und
  Schematron-Failed-Asserts.
- `diagnostics`
  Nur bei `--diagnostics`; zeigt ClassLoader und CodeSource ausgewaehlter
  Konfliktklassen. Bei `--diagnostics-only` ist dies der eigentliche Inhalt des
  Ergebnisses; es gibt dann keine Validierungsfelder wie `schemaValid`.

## Exitcodes

- `0`: `ACCEPTED` oder `DIAGNOSTICS`
- `1`: `REJECTED`
- `2`: `ERROR`
- `64`: falsche CLI-Nutzung

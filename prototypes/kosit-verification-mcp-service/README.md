# KoSIT Verification MCP Service

`kosit-verification-mcp-service` ist der eigenstaendige lokale MCP-Service fuer
Codex und andere lokale Hosts. Er prueft `XRechnung 3.0.2` als `UBL Invoice`
ueber die embedded API des KoSIT-Validators.

Der Prototyp hat zwei Ziele:

- ein lokal startbarer STDIO-MCP-Server, damit Codex XML-Rechnungen direkt
  validieren kann
- ein erstes bewusst maschinenlesbares Ergebnisformat, das dicht an den
  verfuegbaren KoSIT-Daten bleibt

Der Server teilt keine Klassen mit anderen Modulen. Er nutzt das bereits im
Repo commitete XRechnung-Konfigurations-ZIP direkt aus
[`../../bundle-docs/`](../../bundle-docs/), entpackt es bei Bedarf nach
`target/validator-work/` und arbeitet unmittelbar daraus.

## Warum STDIO-MCP

Die erste Version laeuft absichtlich nur ueber STDIO:

- passt direkt zu lokaler Codex-Nutzung
- braucht keine HTTP-Infrastruktur
- haelt den Prototyp klein und bibliotheksnah
- erleichtert einen langlebigen lokalen Prozess mit wiederverwendbarer
  Validatorinstanz
- haelt `stdout` strikt fuer das MCP-Protokoll frei; Diagnose und Logging
  laufen ueber `stderr`

## Scope

Der Server bleibt in v1 beim aktuellen Repo-Scope:

- `XRechnung 3.0.2`
- `UBL Invoice`

Nicht Teil der ersten Version sind:

- `CreditNote`
- `CII`
- `Extension`- und `CVD`-Profile
- HTML-Reports
- fachlich aufbereitete Frontend- oder UI-Sichten

## Build

```bash
mvn -f prototypes/kosit-verification-mcp-service/pom.xml package
```

## Lokal starten

```bash
java -jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar
```

Der Server ist fuer laengere Laufzeit gedacht und wartet nach dem Start auf
MCP-Nachrichten ueber `stdin`/`stdout`.

## Codex lokal eintragen

Beispiel fuer `~/.codex/config.toml`:

```toml
[mcp_servers.kosit_verification]
command = "java"
args = ["-jar", "/path/to/xrechnung-reference-kit/prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar"]
cwd = "/path/to/xrechnung-reference-kit"
enabled = true
required = false
startup_timeout_sec = 20
tool_timeout_sec = 120
enabled_tools = ["xrechnung_validate"]
```

Das Repo muss vorher gebaut sein, damit das JAR unter `target/` existiert.

## Tool

Der Server stellt mindestens ein Tool bereit:

- `xrechnung_validate`

Unterstuetzte Inputs:

- `xmlPath`
  Absoluter Pfad oder Repo-relativer Pfad zu einer XML-Datei.
- `xmlContent`
  Roher XML-String statt Dateipfad.
- `inputName`
  Optionaler logischer Dateiname fuer `xmlContent`.
- `persistArtifacts`
  Optional, Default `false`. Wenn `true`, schreibt der Server die wichtigsten
  Laufartefakte nach `target/runs/<run-id>/`.

Genau eine der beiden Eingabeformen ist erlaubt:

- `xmlPath`
- oder `xmlContent`

Beispiel-Tool-Call:

```json
{
  "xmlPath": "/tmp/invoice-full.xml",
  "persistArtifacts": true
}
```

Das Beispiel nimmt an, dass vorher bereits eine XML-Rechnung erzeugt wurde,
etwa ueber `prototypes/velocity-renderer/`.

Inline-Variante:

```json
{
  "xmlContent": "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">...</Invoice>",
  "inputName": "invoice.xml"
}
```

## Ergebnisformat

Das Tool liefert sein Hauptergebnis als MCP-`structuredContent`. Der Textteil
bleibt bewusst kurz und wiederholt die JSON-Daten nicht vollstaendig.

Der Envelope ist klein und stabil:

- `schemaVersion`
- `toolVersion`
- `input`
- `processingSuccessful`
- `acceptRecommendation`
- `scenario`
- `summary`
- `findings`
- `artifacts`
- `rawKosit`
- optional `toolError`

Details und ein Beispiel stehen in
[`docs/result-format.md`](./docs/result-format.md).

## Persistierte Artefakte

Wenn `persistArtifacts=true` gesetzt ist, schreibt der Server die wichtigsten
Artefakte nach `target/runs/<run-id>/`:

- `input.xml`
- `result.json`
- `report.xml` falls die embedded API einen XML-Report liefert

Zusatzlich registriert der Server eine kleine Resource-Template-Schnittstelle,
ueber die dieselben Artefakte per MCP nachgeladen werden koennen:

- `xrechnung-run://<run-id>/input.xml`
- `xrechnung-run://<run-id>/result.json`
- `xrechnung-run://<run-id>/report.xml`

## Bundle-Nutzung

Der Server kopiert keine Inhalte aus `bundle-docs/` ins Modul. Stattdessen:

1. findet er das commitete ZIP
   `bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`
2. entpackt es bei Bedarf nach `prototypes/kosit-verification-mcp-service/target/validator-work/`
3. initialisiert daraus direkt den embedded KoSIT-Validator

## Offene Punkte

- Die Validatorinstanz wird pro Prozess wiederverwendet, aber konservativ nur
  seriell genutzt; Thread-Safety der tieferen KoSIT-/Saxon-Kette wird in diesem
  Spike nicht aggressiv ausgereizt.
- Es gibt bewusst keinen HTML-Report und keine UI-Aufbereitung.
- `findings` bleiben roh und eng an KoSIT-Resultat und Reportstruktur.
- Der Run-Ordner unter `target/runs/` hat in v1 keine automatische Bereinigung.
- Die MCP-Ressourcen fuer `xrechnung-run://...` werden in v1 nur fuer Runs des
  aktuellen Serverprozesses verwaltet; nach einem Neustart bleiben die Dateien
  auf Platte, werden aber noch nicht wieder eingelesen.
- Der Tool-Scope bleibt enger als das Bundle selbst; das Bundle koennte mehr
  Szenarien, der Server gibt in v1 aber nur `UBL Invoice` frei.
- Das Ergebnisformat ist fuer Maschinenlesbarkeit gedacht, nicht als fertige
  Fach-API fuer Endanwender.

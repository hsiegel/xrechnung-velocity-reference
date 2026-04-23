# KoSIT Via MCP Verifier

`kosit-via-mcp-verifier` ist ein eigenstaendiger Java-Client fuer den lokalen
[`kosit-verification-mcp-service`](../kosit-verification-mcp-service/). Er ruft
KoSIT nicht direkt auf, sondern startet den MCP-Service als Kindprozess und
validiert Rechnungen ueber das Tool `xrechnung_validate`.

Der Prototyp zeigt damit einen entkoppelten Integrationsweg: eine Anwendung
benutzt einen lokalen STDIO-MCP-Service als kontrollierte Prozessgrenze, statt
die KoSIT-Bibliothek selbst einzubinden.

## Rolle des Prototyps

- Showcase und Proof of Concept fuer eine lokale Java-Host-Integration
- keine Dependency auf `org.kosit:validator`
- genau ein lokaler STDIO-Service
- genau ein Tool: `xrechnung_validate`
- kein generischer MCP-Host und keine fertige Produktionsarchitektur

Produktionsrelevante Punkte sind bewusst sichtbar gemacht, aber nicht alle
abschliessend geloest. Details stehen in
[`docs/production-considerations.md`](./docs/production-considerations.md).

## Build

Zuerst den Service bauen:

```bash
mvn -f prototypes/kosit-verification-mcp-service/pom.xml package
```

Dann den Client bauen:

```bash
mvn -f prototypes/kosit-via-mcp-verifier/pom.xml package
```

Das erzeugt:

```bash
prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar
```

## Beispielaufruf

```bash
java -jar prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar \
  --service-jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --persist-artifacts false
```

Optional kann Inline-XML uebergeben werden:

```bash
java -jar prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar \
  --service-jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar \
  --xml-content '<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">...</Invoice>' \
  --input-name inline-invoice.xml
```

Weitere Optionen:

- `--timeout-ms <millis>`
  Default `120000`; gilt in v1 fuer Initialisierung, `tools/list` und
  `tools/call`.
- `--cwd <path>`
  Arbeitsverzeichnis des Serviceprozesses. Default ist der erkannte Repo-Root.
- `--print-raw true|false`
  Wenn `true`, wird das rohe Service-`structuredContent` im Client-Ergebnis
  mit ausgegeben.

## Ausgabe und Exitcodes

Die CLI schreibt zuerst eine Kurzzeile:

- `ACCEPTED`
- `REJECTED`
- `TECHNICAL_FAILURE`

Danach folgt ein kompaktes JSON des gemappten `VerificationResult`.

Exitcodes:

- `0`: fachlich akzeptiert
- `1`: fachlich abgelehnt
- `2`: technische Verarbeitung fehlgeschlagen oder MCP-/Prozessfehler
- `64`: falsche CLI-Nutzung oder Konfigurationsfehler

## Implementierter MCP-Lifecycle

Der Client nutzt die Java-MCP-SDK-Clientseite mit STDIO-Transport:

1. Service-Kindprozess ueber `java -jar <service-jar>` starten.
2. Optionales Arbeitsverzeichnis setzen.
3. Service-`stderr` separat lesen, puffern und als Diagnose nach `stderr`
   spiegeln.
4. MCP `initialize` ausfuehren.
5. `tools/list` aufrufen und `xrechnung_validate` pruefen.
6. `tools/call` mit `xmlPath` oder `xmlContent` ausfuehren.
7. `structuredContent` in ein eigenes Client-Modell mappen.
8. MCP-Client und Serviceprozess geordnet schliessen.

`stdout` des Service bleibt dabei ausschliesslich MCP-Protokoll. Diagnose des
Kindprozesses wird nicht mit dem Protokoll vermischt.

## Ergebnis-Mapping

Der Client unterscheidet drei Ebenen:

- Fachlich ungueltig:
  MCP-Aufruf erfolgreich, `processingSuccessful=true`,
  `acceptRecommendation` nicht akzeptierend, Exit `1`.
- Technische Verarbeitung im Service fehlgeschlagen:
  MCP-Aufruf erfolgreich, `processingSuccessful=false`, `toolError` im
  Service-Ergebnis, Exit `2`.
- MCP-/Transport-/Prozessfehler:
  Initialisierung, `tools/list`, `tools/call`, JSON-/Protokoll oder Prozess
  scheitern; der Client erzeugt `technicalFailure`, Exit `2`.

Wichtige Klassen:

- `KositViaMcpVerifierCli`
  CLI, Exitcodes und JSON-Ausgabe.
- `InvoiceVerifier`
  kleine fachliche Schnittstelle.
- `McpInvoiceVerifier`
  MCP-basierte Implementierung von `InvoiceVerifier`.
- `McpServiceProcess`
  Prozesskonfiguration fuer den lokalen Service.
- `WorkingDirectoryStdioClientTransport`
  kleine SDK-Transportanpassung fuer `cwd`.
- `McpToolInvoker`
  `tools/list`, Tool-Pruefung und `tools/call`.
- `VerificationResult`, `VerificationFinding`, `VerificationSummary`
  internes Ergebnisformat des Clients.
- `VerificationTechnicalFailure`
  technische Fehler jenseits fachlicher Validierungsbefunde.

## Smoke-Test

Nach beiden Maven-Builds:

```bash
java -jar prototypes/kosit-via-mcp-verifier/target/kosit-via-mcp-verifier.jar \
  --service-jar prototypes/kosit-verification-mcp-service/target/kosit-verification-mcp-service.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --persist-artifacts false
```

`ACCEPTED` oder `REJECTED` zeigt, dass Prozessstart, MCP-Lifecycle und
Tool-Aufruf funktioniert haben. `TECHNICAL_FAILURE` zeigt einen technischen
Fehler in Client, Service, Bundle oder Eingabe an.

## Bewusst offene Punkte

- keine Prozesspools
- keine parallele Nutzung eines Serviceprozesses
- keine Restart-Strategie nach Prozessabbruch
- keine harten XML-, Ergebnis- oder Artefaktgroessenlimits
- kein Run-Artefakt-Cleanup
- kein Monitoring-/Metrikexport
- keine Clusterkoordination
- keine produktionsreife Pfad-Sandbox fuer `xmlPath`

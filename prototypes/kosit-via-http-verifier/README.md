# KoSIT Via HTTP Verifier

`kosit-via-http-verifier` ist ein eigenstaendiger Java-Client-/Host-Prototyp fuer
lokale XRechnung-Validierung ueber eine HTTP-Prozessgrenze. Die CLI bindet KoSIT
nicht direkt fuer die eigentliche Validierung ein, sondern startet beim ersten
Validierungszugriff einen lokalen Worker-Kindprozess. Dieser Worker bindet auf
`127.0.0.1`, nimmt JSON per HTTP an und liefert ein maschinenlesbares
Validierungsergebnis zurueck.

Der Prototyp ist absichtlich standalone:

- kein Code- oder Laufzeitaufruf des MCP-Prototyps
- eigene Maven-Abhaengigkeiten und ein eigenes Fat-JAR
- gemeinsame Nutzung nur der versionierten Validator-Konfiguration aus
  [`../../bundle-docs/`](../../bundle-docs/)

## Rolle des Prototyps

- Proof of Concept fuer eine lokale HTTP-Prozessgrenze rund um KoSIT
- lazy Worker-Start erst beim ersten `verify`-Aufruf
- Host-CLI mit vertrauter Ausgabe: `ACCEPTED`, `REJECTED` oder
  `TECHNICAL_FAILURE`, danach kompaktes JSON
- interner HTTP-Worker mit `GET /health`, `POST /validate` und
  `POST /shutdown`
- kein frei erreichbarer HTTP-Service und keine stabile externe HTTP-API

Produktionsrelevante Punkte stehen in
[`docs/production-considerations.md`](./docs/production-considerations.md).

## Was der Prototyp zeigt

Der Prototyp zeigt:

- Eine Host-Seite kann KoSIT nutzen, ohne KoSIT in demselben Prozess
  auszufuehren.
- Die Prozessgrenze kann statt STDIO/MCP auch als lokaler HTTP-Kanal modelliert
  werden.
- Der Worker kann lazy gestartet werden und meldet den dynamisch gewaehlten Port
  ueber eine einzelne Readiness-Zeile.
- Der Host kann fachliche Validierungsergebnisse, technische Worker-Fehler und
  Transport-/Timeoutfehler getrennt mappen.
- Ein haengender Validierungslauf kann ueber einen Host-seitigen Timeout als
  technische Failure beendet werden.

Der Prototyp zeigt bewusst noch nicht:

- eine frei erreichbare HTTP-API
- Prozesspooling oder parallele Worker
- produktionsreife Pfad-, Artefakt- oder Speicherverwaltung
- vollstaendige Observability mit Metriken und korreliertem Logging
- eine finale Lifecycle-Integration in eine lang laufende Host-Anwendung

## Build

```bash
mvn -f prototypes/kosit-via-http-verifier/pom.xml package
```

Das erzeugt:

```bash
prototypes/kosit-via-http-verifier/target/kosit-via-http-verifier.jar
```

## Beispielaufruf

```bash
java -jar prototypes/kosit-via-http-verifier/target/kosit-via-http-verifier.jar \
  --xml bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml \
  --persist-artifacts false
```

Inline-XML ist ebenfalls moeglich:

```bash
java -jar prototypes/kosit-via-http-verifier/target/kosit-via-http-verifier.jar \
  --xml-content '<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">...</Invoice>' \
  --input-name inline-invoice.xml
```

## Optionen

- `--xml <path>`
  XML-Datei, die validiert werden soll.
- `--xml-content <xml>`
  Inline-XML statt Dateipfad.
- `--input-name <name>`
  Optionaler logischer Dateiname fuer Inline-XML.
- `--persist-artifacts true|false`
  Optional; Default `false`. Wenn `true`, schreibt der Worker `input.xml`,
  `result.json` und ggf. `report.xml` nach `target/runs/<run-id>/`.
- `--timeout-ms <millis>`
  Optional; Default `120000`; gilt fuer Worker-Startup und Shutdown.
- `--validation-timeout-ms <millis>`
  Optional; Default `300000`. Wenn eine Validierung so lange keine Antwort
  liefert, klassifiziert der Host den Request als technische Failure und
  beendet den Workerprozess hart.
- `--cwd <path>`
  Arbeitsverzeichnis des Workerprozesses. Default ist der erkannte Repo-Root.
- `--print-raw true|false`
  Optional; Default `false`. Wenn `true`, enthaelt das Client-JSON zusaetzlich
  das rohe Worker-Ergebnis.

Genau eine der beiden Eingabeformen ist erlaubt:

- `--xml`
- oder `--xml-content`

## Ausgabe und Exitcodes

Die CLI schreibt zuerst eine Kurzzeile:

- `ACCEPTED`
- `REJECTED`
- `TECHNICAL_FAILURE`

Danach folgt ein kompaktes JSON des gemappten `VerificationResult`.

Exitcodes:

- `0`: fachlich akzeptiert
- `1`: fachlich abgelehnt
- `2`: technische Verarbeitung fehlgeschlagen oder HTTP-/Prozessfehler
- `64`: falsche CLI-Nutzung oder Konfigurationsfehler

## Interner HTTP-Lifecycle

Der normale CLI-Modus startet den Worker nicht beim Programmstart, sondern erst
im ersten `verify`-Aufruf:

1. Host erzeugt ein zufaelliges Bearer-Token.
2. Host startet dasselbe JAR als Kindprozess mit internem `--http-worker`.
3. Worker bindet auf `127.0.0.1:0`; der OS waehlt einen freien Port.
4. Worker schreibt genau eine JSON-Readiness-Zeile nach `stdout`.
5. Host ruft `POST /validate` mit Bearer-Token und JSON-Request auf.
6. Worker validiert seriell ueber embedded KoSIT und liefert JSON zurueck.
7. Wenn die Validierung laenger als `--validation-timeout-ms` laeuft, bricht
   der Host den Worker ab. Der naechste Zugriff startet wieder lazy einen neuen
   Worker.
8. Beim Schliessen ruft der Host `POST /shutdown` auf und beendet den Prozess
   notfalls hart nach Timeout.

Der Worker haelt `stdout` fuer die Readiness-Zeile frei. Diagnose laeuft ueber
`stderr` und wird vom Host gepuffert.

## Implementierung

Der Prototyp besteht aus zwei Modi im selben ausfuehrbaren JAR:

- Normaler CLI-Modus:
  parst Eingabe, erzeugt einen `HttpInvoiceVerifier`, startet den Worker lazy
  und mappt die Worker-Antwort in das eigene Client-Ergebnis.
- Interner Worker-Modus:
  wird nur ueber `--http-worker` gestartet, initialisiert den embedded
  KoSIT-Validator und bedient die lokalen HTTP-Endpunkte.

Der Host startet den Worker als Kindprozess mit `java -jar <current-jar>`.
Dadurch bleibt der Prototyp standalone: Build-Artefakt, Worker-Code und
Host-Code liegen in einem Modul, teilen sich aber nur das
Validator-Konfigurationsbundle unter `bundle-docs/`.

Die eigentliche Validierung laeuft weiterhin ueber die KoSIT-API im
Workerprozess. Die HTTP-Grenze ist also keine fachliche Neuentwicklung der
Validierung, sondern eine Integrations- und Fehlerisolationsgrenze.

## Worker-HTTP

Die HTTP-Schnittstelle ist intern fuer den Host-Prototyp gedacht:

- `GET /health`
  Antwortet mit kleinem JSON, wenn das Bearer-Token stimmt.
- `POST /validate`
  Erwartet JSON mit `xmlPath` oder `xmlContent`, optional `inputName` und
  `persistArtifacts`.
- `POST /shutdown`
  Loest den geordneten Worker-Shutdown aus.

Alle Endpunkte verlangen `Authorization: Bearer <token>`. Der Worker akzeptiert
standardmaessig maximal 10 MiB Request-Body.

## Ergebnis-Mapping

Der Host unterscheidet drei Ergebnisarten:

- Fachlich akzeptiert:
  Worker-Aufruf erfolgreich, `processingSuccessful=true`,
  `acceptRecommendation=ACCEPTABLE`, Exit `0`.
- Fachlich abgelehnt:
  Worker-Aufruf erfolgreich, `processingSuccessful=true`, aber keine
  akzeptierende Empfehlung, Exit `1`.
- Technische Failure:
  KoSIT-Verarbeitung, HTTP-Transport, Worker-Start, JSON-Mapping oder
  Validierungs-Timeout scheitern, Exit `2`.

Bei einem Validierungs-Timeout erzeugt der Host die Kategorie
`validation_timeout`, beendet den aktuellen Workerprozess hart und laesst einen
Folgeaufruf wieder lazy einen neuen Worker starten.

## Zentrale Klassen und Loesungsskizzen

Die wichtigsten Entscheidungen sind an kleine Klassen gebunden. Diese Tabelle
zeigt, welcher Aspekt wo geloest ist und wie die jeweilige Loesung grob
funktioniert:

| Aspekt | Klassen | Loesungsskizze |
|---|---|---|
| Zwei Modi in einem JAR | [`KositViaHttpVerifierCli`](./src/main/java/local/xrechnung/kositviahttpverifier/KositViaHttpVerifierCli.java) | `main` setzt nur Logging-Defaults, prueft als erstes auf `--http-worker` und verzweigt dann entweder in den internen Worker-Modus oder in den normalen CLI-Modus. Der normale Modus parst Argumente, erzeugt den Host-Verifier im `try`-Block, druckt Statuszeile plus JSON und setzt die Exitcodes `0`, `1`, `2` oder `64`. |
| Externer CLI-Vertrag | [`CliArguments`](./src/main/java/local/xrechnung/kositviahttpverifier/CliArguments.java), [`VerificationRequest`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationRequest.java) | Das Parsing bleibt absichtlich simpel: bekannte `--key value`-Paare, genau eine Eingabequelle aus `--xml` oder `--xml-content`, Boolean-Optionen strikt als `true`/`false`. `--timeout-ms` steuert Startup/Shutdown, `--validation-timeout-ms` den eigentlichen Validierungslauf. Aus den CLI-Werten entsteht ein transportneutrales `VerificationRequest`. |
| Kleine Host-Schnittstelle | [`InvoiceVerifier`](./src/main/java/local/xrechnung/kositviahttpverifier/InvoiceVerifier.java), [`VerificationResult`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationResult.java), [`VerificationTechnicalFailure`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationTechnicalFailure.java) | Der Host-Code sieht nur `verify(request) -> VerificationResult`. Fachliche Ergebnisse und technische Fehler werden nicht ueber Exceptions gemischt: Exceptions transportieren nur technische Failures, die CLI wandelt sie wieder in ein Ergebnis mit `TECHNICAL_FAILURE` um. |
| Lazy Worker-Lifecycle im Host | [`HttpInvoiceVerifier`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpInvoiceVerifier.java), [`HttpWorkerProcess`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpWorkerProcess.java) | `HttpInvoiceVerifier.verify(...)` ist synchronisiert und ruft `ensureWorker()` erst beim ersten Validierungszugriff auf. `HttpWorkerProcess` startet dasselbe Fat-JAR als Kindprozess, liest genau eine JSON-Readiness-Zeile von `stdout` und baut daraus die lokale Base-URI. `close()` ruft `/shutdown` auf und beendet den Prozess nach Timeout notfalls hart. |
| Lokales HTTP-Protokoll | [`HttpWorkerServer`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpWorkerServer.java), [`JsonSupport`](./src/main/java/local/xrechnung/kositviahttpverifier/JsonSupport.java) | Der Worker nutzt den JDK-`HttpServer`, bindet auf `127.0.0.1` und Port `0`, bedient `/health`, `/validate` und `/shutdown` seriell in einem Single-Thread-Executor und verlangt auf jedem Endpunkt das pro Start zufaellige Bearer-Token. JSON wird zentral ueber Jackson gelesen und geschrieben; der Request-Body ist auf 10 MiB begrenzt. |
| Timeout-Fehlerisolation | [`HttpTimeouts`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpTimeouts.java), [`HttpInvoiceVerifier`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpInvoiceVerifier.java), [`HttpWorkerProcess`](./src/main/java/local/xrechnung/kositviahttpverifier/HttpWorkerProcess.java) | Startup/Shutdown und Validierung sind getrennte Timeouts. Wenn der HTTP-Request nach `/validate` den Validierungs-Timeout ueberschreitet, erzeugt der Host `validation_timeout`, verwirft die Worker-Referenz und beendet den Kindprozess hart. Der naechste Aufruf startet wieder lazy einen neuen Worker. |
| Worker-seitige KoSIT-Integration | [`ValidationService`](./src/main/java/local/xrechnung/kositviahttpverifier/ValidationService.java), [`ReusableValidator`](./src/main/java/local/xrechnung/kositviahttpverifier/ReusableValidator.java), [`ValidatorConfigSupport`](./src/main/java/local/xrechnung/kositviahttpverifier/ValidatorConfigSupport.java), [`RepositoryLocator`](./src/main/java/local/xrechnung/kositviahttpverifier/RepositoryLocator.java) | Der Worker sucht vom Arbeitsverzeichnis aus den Repo-Root, findet das versionierte Validator-Konfigurations-ZIP unter `bundle-docs/`, entpackt es nach `target/validator-work/` und initialisiert daraus einmal eine wiederverwendete KoSIT-Instanz. Die Validierung selbst bleibt damit KoSIT-API, nur in einem getrennten Prozess. |
| Eingabeaufloesung und Scope-Grenze | [`ValidationInputResolver`](./src/main/java/local/xrechnung/kositviahttpverifier/ValidationInputResolver.java), [`ValidationInput`](./src/main/java/local/xrechnung/kositviahttpverifier/ValidationInput.java), [`InvoiceScopeInspector`](./src/main/java/local/xrechnung/kositviahttpverifier/InvoiceScopeInspector.java) | Der Worker akzeptiert entweder `xmlPath` relativ zum Repo-Root oder `xmlContent` als Inline-Payload. Danach liest `InvoiceScopeInspector` Root-Element und `CustomizationID`, damit der Prototyp explizit nur den unterstuetzten UBL-Invoice/XRechnung-Scope validiert und andere Profile als technische Tool-Failure zurueckweist. |
| KoSIT-Report zu JSON | [`KositReportParser`](./src/main/java/local/xrechnung/kositviahttpverifier/KositReportParser.java), [`ValidationResultMapper`](./src/main/java/local/xrechnung/kositviahttpverifier/ValidationResultMapper.java), [`ToolFailure`](./src/main/java/local/xrechnung/kositviahttpverifier/ToolFailure.java) | Nach dem KoSIT-Lauf wird der XML-Report analysiert: XSD-/Schematron-Zaehler, Findings, Szenario und Akzeptanzempfehlung werden in ein strukturiertes Worker-Ergebnis gemappt. Erwartbare technische Probleme laufen ueber `ToolFailure`, unerwartete Exceptions werden in eine generische Fehler-Envelope ueberfuehrt. |
| Client-seitiges Ergebnismodell | [`VerificationResultMapper`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationResultMapper.java), [`VerificationFinding`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationFinding.java), [`VerificationSummary`](./src/main/java/local/xrechnung/kositviahttpverifier/VerificationSummary.java) | Der Host uebernimmt nicht blind das Worker-JSON, sondern mappt es in ein eigenes Ergebnisformat. Dadurch bleiben fachliche Ablehnung, Worker-seitige technische Verarbeitung und HTTP-/Prozessfehler einheitlich fuer die CLI sichtbar. Optional kann das rohe Worker-Ergebnis ueber `--print-raw` mit ausgegeben werden. |
| Run-Artefakte | [`RunArtifactStore`](./src/main/java/local/xrechnung/kositviahttpverifier/RunArtifactStore.java) | Wenn `persistArtifacts=true` gesetzt ist, erzeugt der Worker einen Run-Ordner, schreibt `input.xml`, den KoSIT-`report.xml` und `result.json` und gibt die Pfade im Ergebnisblock zurueck. Ohne Persistenz bleiben die Artefaktpfade leer; `reportXmlAvailable` zeigt trotzdem, ob ein KoSIT-Report erzeugt wurde. |
| XML- und JSON-Hilfen | [`XmlSupport`](./src/main/java/local/xrechnung/kositviahttpverifier/XmlSupport.java), [`JsonSupport`](./src/main/java/local/xrechnung/kositviahttpverifier/JsonSupport.java) | Kleine Hilfsklassen kapseln sichere XML-Factory-Konfiguration, XPath-/DOM-Zugriff und JSON-Serialisierung. Das haelt die fachlichen Klassen frei von Parser-Setup und macht die Stellen sichtbar, an denen XML/JSON-Verhalten spaeter zentral gehaertet werden kann. |

## Aenderungspunkte fuer Implementierer

| Aufgabe | Zentrale Stelle |
|---|---|
| CLI-Option hinzufuegen | `CliArguments`, danach ggf. `VerificationRequest` oder `HttpTimeouts` |
| Worker-Start anders konfigurieren | `HttpWorkerProcess.command()` und `CliArguments.toWorkerProcess()` |
| HTTP-Endpunkt erweitern | `HttpWorkerServer` plus ein Test in `HttpWorkerServerTest` |
| Validierungs-Timeout oder Restart-Verhalten anpassen | `HttpTimeouts`, `HttpInvoiceVerifier`, `HttpWorkerProcess` |
| KoSIT-Bundle oder Arbeitsverzeichnis anders aufloesen | `RepositoryLocator`, `ValidatorConfigSupport`, `ValidationService.create(...)` |
| andere Eingabequellen erlauben | `ValidationInputResolver`, `ValidationInput`, `VerificationRequest` |
| unterstuetzten Rechnungs-Scope erweitern | `InvoiceScopeInspector` und die Scope-Tests |
| Worker-Ergebnis erweitern | `ValidationResultMapper`, `KositReportParser`, `RunArtifactStore` und danach `VerificationResultMapper` |
| Client-JSON veraendern | `VerificationResult`, `VerificationFinding`, `VerificationSummary`, `VerificationTechnicalFailure` |
| persistierte Artefakte anders ablegen | `RunArtifactStore` und die Pfadkonfiguration in `ValidationService` |

## Bundle-Nutzung

Der Worker kopiert keine Inhalte aus `bundle-docs/` ins Modul. Stattdessen:

1. findet er das commitete ZIP
   `bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`
2. entpackt es bei Bedarf nach
   `prototypes/kosit-via-http-verifier/target/validator-work/`
3. initialisiert daraus den embedded KoSIT-Validator

Der aktuelle Scope entspricht den anderen KoSIT-Prototypen:

- `XRechnung 3.0.2`
- `UBL Invoice`
- keine `CreditNote`, `CII`, Extension- oder CVD-Profile

## Uebertragbarkeit auf reale Szenarien

Der Ansatz ist auf lang laufende Host-Anwendungen uebertragbar, wenn die
Prozessgrenze als interne lokale Komponente behandelt wird:

- Jede Host-Instanz besitzt ihren lokalen Worker oder einen lokalen,
  kontrollierten Worker-Pool.
- Der HTTP-Port bleibt dynamisch und wird nicht in externe Konfiguration oder
  Service Discovery aufgenommen.
- Der Host besitzt den Workerprozess, beendet ihn bei Shutdown und verwirft ihn
  nach Timeout oder Protokollbruch.
- Fachliche Rejects und technische Failures bleiben fuer aufrufende Anwendung,
  Logs und Monitoring getrennt.
- Dateipfade, Arbeitsverzeichnisse, Artefaktablage und Cleanup werden von der
  jeweiligen Umgebung vorgegeben und muessen ausserhalb dieses Prototyps
  eingeschraenkt werden.
- Speicherlimits, Metriken und eine Restart-Strategie sind Betriebsfragen, die
  dieser Prototyp vorbereitet, aber nicht final entscheidet.

## Tests

```bash
mvn -f prototypes/kosit-via-http-verifier/pom.xml test
```

Die Tests decken CLI-Parsing, Input-Aufloesung, Scope-Pruefung und die
HTTP-Handler fuer Authentisierung, JSON-Request und Body-Limit ab.

## Bewusst offene Punkte

- kein Prozesspool und keine parallele Validierung im Worker
- keine Parent-PID-Ueberwachung durch den Worker
- keine konfigurierbaren JVM-Speicherlimits fuer den Workerprozess
- kein automatisches Cleanup persistierter Run-Artefakte
- keine produktionsreife Pfad-Sandbox fuer `xmlPath`
- keine Metrik- oder Healthcheck-Integration ausser dem internen `/health`
- keine stabile externe HTTP-API

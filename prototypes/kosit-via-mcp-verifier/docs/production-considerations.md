# Production Considerations

Dieser Client-Prototyp zeigt die Integrationskante zu einem lokalen
KoSIT-MCP-Service. Er ist noch keine produktionsfertige Betriebsarchitektur.

## Prozess-Lifecycle

- Start beim ersten Request ist einfach, kostet aber Latenz pro Kaltstart.
- Start beim Modulstart reduziert Request-Latenz, braucht aber Healthchecks und
  Restart-Logik.
- In einem Cluster haette jede Host-Anwendungsinstanz eigene lokale
  Serviceprozesse oder einen lokalen Prozesspool.
- Shutdown-Hooks muessen geordnet beenden, duerfen aber nicht die Anwendung
  blockieren.

## Parallelitaet

- Der Prototyp nutzt einen Serviceprozess fuer einen CLI-Request.
- Thread-Safety der gesamten KoSIT-/Saxon-/MCP-Kette wird nicht angenommen.
- Ein Produktionsbetrieb braucht eine Entscheidung: serielle Queue,
  Prozesspool oder anderweitig isolierte Worker.
- Backpressure und Queueing muessen verhindern, dass viele Rechnungen beliebig
  viele Prozesse oder Speicher belegen.

## Timeouts und Limits

- Startup-/Initialisierungs-Timeout, `tools/list`-Timeout und Tool-Call-Timeout
  sind im Code als Konfigurationsstelle sichtbar.
- V1 nutzt einen gemeinsamen `--timeout-ms`; produktiv sollten die Werte
  getrennt konfigurierbar sein.
- Maximale XML-Groesse, maximale Ergebnisgroesse und maximale Artefaktgroesse
  sind noch nicht enforced.
- Bei Timeout muss der Prozess abgebrochen und der Request eindeutig als
  technische Failure klassifiziert werden.

## Logging und Protokoll

- Service-`stdout` ist ausschliesslich MCP-Protokoll.
- Service-`stderr` ist Diagnose und wird vom Client separat gelesen.
- Produktionslogging braucht Korrelation pro Request, ohne XML-Inhalte
  unkontrolliert in Logs zu schreiben.

## Security

- Es gibt keinen frei erreichbaren Port, aber einen lokalen Prozess mit
  Dateisystemzugriff.
- `xmlPath` muss produktiv gegen erlaubte Verzeichnisse validiert werden.
- `xmlContent` vermeidet Pfadfreigaben, kann aber grosse Payloads und
  temporare Datei-/Speicherfragen erzeugen.
- Service-JAR und Arbeitsverzeichnis muessen konfigurierbar, aber kontrolliert
  sein.

## Artefakte und Cleanup

- `persistArtifacts=true` schreibt Run-Artefakte unter dem Service-`target/`.
- V1 hat kein automatisches Cleanup von `target/runs`.
- Produktiv braucht es Quotas, Retention, Diagnosezugriff und sichere Loeschung.

## Versionierung und Updates

- MCP-Service-Version, Tool-Schema-Version und Ergebnis-Schema-Version muessen
  kompatibilitaetsbewusst behandelt werden.
- Bundle-Updates des KoSIT-Service koennen Ergebnisstruktur, Findings und
  Accept-/Reject-Details veraendern.
- Der Client sollte bei `tools/list` und spaeter ggf. ueber Schema-/Version-
  Felder pruefen, ob er den Service versteht.

## Monitoring

- Wichtige Metriken: Validierungsanzahl, Dauer, Accept/Reject, technische
  Fehlerarten, Timeouts, Restarts, Queue-Laenge, Artefaktgroessen.
- Healthchecks koennen ueber Prozessstatus, `ping` oder `tools/list` laufen.
- Transportfehler und fachliche Rejects muessen getrennt berichtet werden.

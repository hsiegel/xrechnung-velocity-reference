# Production Considerations

Dieser Prototyp zeigt eine lokale HTTP-Prozessgrenze rund um KoSIT. Er ist noch
keine produktionsfertige Betriebsarchitektur.

## Neben einer Host-Anwendung

- Der Worker bindet nur an `127.0.0.1` und nutzt in v1 Port `0`, damit kein
  statischer Port mit anderen Diensten der Maschine kollidiert.
- Der ausgewaehlte Port wird nur ueber die Kindprozess-Readiness an den Host
  weitergegeben; er gehoert nicht in externe Konfiguration oder Service
  Discovery.
- In Produktion sollte der Host den Workerprozess besitzen, ueberwachen und
  beenden. Ein frei gestarteter HTTP-Worker waere eine andere Betriebsform und
  braucht eigene Hardening-Regeln.
- Logs muessen strikt getrennt bleiben: Worker-`stdout` ist technische
  Prozesskommunikation, Worker-`stderr` ist Diagnose.

## Prozess-Lifecycle

- Lazy Start beim ersten Request spart Startkosten im ungenutzten Pfad, erzeugt
  aber Kaltstart-Latenz fuer den ersten Validierungsrequest.
- Start beim Anwendungsstart reduziert Request-Latenz, braucht aber
  Healthchecks, Restart-Logik und klares Fehlerverhalten beim Boot.
- Bei Timeout oder Protokollbruch muss der Host den Worker abbrechen und den
  Request als technische Failure klassifizieren.
- Der Prototyp hat einen separaten Validierungs-Timeout. Bei Ablauf verwirft der
  Host den aktuellen Workerprozess; ein Folgerequest startet lazy einen frischen
  Worker.
- Shutdown-Hooks duerfen Stopps der Host-Anwendung nicht blockieren; der Host
  braucht eine harte Obergrenze fuer Worker-Shutdown.

## Parallelitaet

- Der Prototyp nutzt einen Workerprozess und einen seriellen HTTP-Executor.
- Die KoSIT-/Saxon-Kette wird nicht als thread-safe angenommen.
- Produktiv muss explizit gewaehlt werden: serielle Queue, begrenzter
  Prozesspool oder andere Worker-Isolation.
- Backpressure verhindert, dass viele Rechnungen beliebig viele Prozesse,
  Threads oder Speicher belegen.
- Speicherlimits sind fuer spaeter notiert: produktiv sollte der Worker mit
  expliziten JVM-Grenzen wie `-Xmx` gestartet und anhand echter Rechnungen
  dimensioniert werden.

## HTTP-Sicherheit

- Loopback-Bindung und zufaelliges Bearer-Token schuetzen nur gegen versehentliche
  lokale Fehlaufrufe, nicht gegen alle Angriffe auf einem kompromittierten Host.
- Kein Endpunkt darf auf `0.0.0.0` gebunden werden, solange keine echte
  Authentisierung, Autorisierung, TLS- und Netzwerkpolicy existiert.
- Request-Body-Groessen, Ergebnisgroessen und Artefaktgroessen brauchen harte
  Limits und sinnvolle Fehlerklassifikation.
- XML-Inhalte gehoeren nicht unkontrolliert in Logs.

## Pfade und Artefakte

- `xmlPath` ist bequem fuer lokale Tests, muss produktiv aber auf erlaubte
  Verzeichnisse oder einen kontrollierten Spool-Bereich eingeschraenkt werden.
- `xmlContent` vermeidet Dateipfadfreigaben, braucht aber Payload-Limits und
  Speicher-/Tempfile-Regeln.
- `persistArtifacts=true` schreibt unter `target/runs`; produktiv braucht es
  Quotas, Retention, Diagnosezugriff und sichere Loeschung.

## Monitoring

- Wichtige Metriken: Worker-Starts, Startup-Dauer, Validierungsdauer,
  Accept/Reject, technische Fehlerarten, Timeouts, Restarts, Queue-Laenge und
  Artefaktgroessen.
- Healthchecks koennen Prozessstatus und `GET /health` kombinieren.
- Fachliche Rejects und technische Fehler muessen in Monitoring und API-Antworten
  getrennt bleiben.

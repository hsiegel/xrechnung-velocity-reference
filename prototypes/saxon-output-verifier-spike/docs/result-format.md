# Ergebnisformat

Der Verifier schreibt ein bewusst kleines JSON-Ergebnis fuer den
profilgesteuerten Ausgangsrechnungs-Pfad. Das Format ist nicht KoSIT-kompatibel
und will das auch nicht vortaeuschen. Es beschreibt den aktuellen
Verifier-Kern.

## Leitidee

- kleiner stabiler Envelope
- roh genug fuer Maschinen und Regressionstests
- nah an Profil-Gate, JAXP-XSD und Saxon-SVRL
- keine fachliche Schoenfaerberei
- kein Ersatz fuer KoSIT-Report-XML

## Top-Level-Felder

- `schemaVersion`
  Version des JSON-Vertrags, aktuell `1.0.0`.
- `toolVersion`
  Modulversion des Verifiers.
- `profile`
  Das ausgewaehlte Erwartungsprofil, aktuell z. B. `xrechnung-ubl-invoice`.
- `input`
  Eingabe- und Arbeitsordner-Informationen.
- `processingSuccessful`
  `true`, wenn Profil-Gate/XSD/Schematron technisch sauber liefen.
- `verificationVerdict`
  Einer von `PASS`, `FAIL`, `ERROR`.
- `summary`
  Kleine Uebersicht fuer schnelle Entscheidungen.
- `findings`
  Flaches Array roher Befunde.
- `artifacts`
  Pfade zu `result.json` und den SVRL-Artefakten.
- `rawVerifier`
  Rohblock mit Match-XPath, XSD-Pfad, XSD-Findings und SVRL-Stufen.
- `toolError`
  Nur bei technischen Problemen gesetzt.

## Summary

`summary` enthaelt aktuell:

- `profileMatched`
- `schemaValid`
- `schematronExecuted`
- `schematronValid`
- `xsdFindingCount`
- `schematronFindingCount`
- `findingCountTotal`

`schemaValid` und `schematronValid` koennen `null` sein, wenn eine fruehere
Stufe den Lauf bereits beendet hat.

## Findings

`findings[]` bleibt flach und nuchtern. Ein Eintrag kann diese Felder tragen:

- `channel`
  `custom`, `xsd` oder `schematron`
- `severity`
- `message`
- `location`
- `ruleId`
- `test`
- `flag`
- `role`
- `rawType`
- `line`
- `column`
- `stepId`

Beispiele:

- Profil-Gate-Mismatch:
  `channel=custom`, `rawType=profile-gate`
- XSD-Verstoss:
  `channel=xsd`, `rawType=xsd-validation`
- SVRL `failed-assert`:
  `channel=schematron`, `rawType=failed-assert`

## Rohblock

`rawVerifier` zeigt den technischen Unterbau des aktuellen Eigenbaus:

- `selectedProfileId`
- `selectedScenarioName`
- `matchExpression`
- `matchExpressionResult`
- `xmlSchemaPath`
- `xsdFindings`
- `svrlArtifacts`

Die `svrlArtifacts` enthalten pro Schematron-Stufe:

- `resourceLocation`
- `stylesheet`
- `outputPath`
- `failedAsserts`
- `successfulReports`

## Wichtige Grenze

Dieses Format sagt:

- was gegen das erwartete Ausgangsprofil geprueft wurde
- welche XSD- und SVRL-Befunde dabei entstanden sind
- ob der kleine Verifier-Kern `PASS`, `FAIL` oder `ERROR` empfiehlt

Dieses Format sagt explizit noch nicht:

- was ein KoSIT-Report als VARL-XML ausgeben wuerde
- wie `customLevel`-Overrides die finale Akzeptanz aendern wuerden
- dass die Entscheidung Ergebnisparitaet zur KoSIT-Laufzeit hat

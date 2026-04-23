# Ergebnisformat

Das Tool `xrechnung_validate` liefert sein primaeres Resultat als
MCP-`structuredContent`. Das Format ist absichtlich klein im Envelope und roh im
Detail.

## Leitidee

- Der Envelope soll fuer Codex und andere Maschinen stabil lesbar bleiben.
- `rawKosit` soll nah an den verfuegbaren Bibliotheksdaten bleiben.
- `findings` sollen ein flaches, direkt weiterverarbeitbares Analysearray
  bieten.
- Fachlich ungueltige Dokumente sind kein Toolfehler.
- Technische Toolfehler werden mit `isError = true` und einem `toolError`-Block
  geliefert.

## Top-Level-Felder

- `schemaVersion`
  Version des Ergebnisformats, aktuell `1.0.0`.
- `toolVersion`
  Modul-/Serverversion.
- `input`
  Informationen ueber die verwendete Eingabe.
- `processingSuccessful`
  Technische Ausfuehrung der Validatorbibliothek.
- `acceptRecommendation`
  Direkte KoSIT-Empfehlung, z. B. `ACCEPTABLE` oder `REJECT`.
- `scenario`
  Informationen ueber das gematchte KoSIT-Szenario oder `noScenarioMatched`.
- `summary`
  Kleine Uebersicht fuer schnelle Entscheidungen.
- `findings`
  Flaches Array technischer oder fachlicher Befunde.
- `artifacts`
  Pfade und Resource-URIs zu persistierten Laufartefakten.
- `rawKosit`
  Rohblock mit KoSIT-nahen Daten.
- `toolError`
  Nur bei echten Toolfehlern gesetzt.

## Finding-Form

`findings[]` bleibt flach und nuchtern. Ein Eintrag kann diese Felder tragen:

- `channel`
  Einer von `xsd`, `schematron`, `processing`, `custom`.
- `severity`
  Rohe oder nur leicht normalisierte Schwereangabe.
- `message`
  Kurztext des Befunds.
- `location`
  XPath- oder Zeilen-/Spaltenort, falls vorhanden.
- `ruleId`
  Regel- oder Fehlercode, falls vorhanden.
- `test`
  Schematron-Testausdruck, falls vorhanden.
- `flag`
  Schematron-Flag, falls vorhanden.
- `role`
  Schematron-Role, falls vorhanden.
- `rawType`
  Herkunft des Befunds, z. B. `XmlError`, `FailedAssert`, `report-message`.
- `line`
- `column`
- `stepId`

## Beispiel

```json
{
  "schemaVersion": "1.0.0",
  "toolVersion": "1.0-SNAPSHOT",
  "input": {
    "source": "xmlPath",
    "inputName": "invoice.xml",
    "resolvedPath": "/tmp/invoice.xml",
    "sha256Base64": "VbSXhrPpuVPUz9hL5i1Ly2cHynrew6ptt6NyzpQVC34=",
    "persistArtifacts": true
  },
  "processingSuccessful": true,
  "acceptRecommendation": "REJECT",
  "scenario": {
    "matched": true,
    "name": "EN16931 XRechnung (UBL Invoice)",
    "customizationId": "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"
  },
  "summary": {
    "wellformed": true,
    "schemaValid": true,
    "schematronValid": false,
    "processingErrorCount": 0,
    "xsdFindingCount": 0,
    "schematronFindingCount": 1,
    "findingCountTotal": 1
  },
  "findings": [
    {
      "channel": "schematron",
      "severity": "fatal",
      "message": "[BR-CO-16]-Amount due for payment (BT-115) = Invoice total amount with VAT (BT-112) -Paid amount (BT-113) +Rounding amount (BT-114).",
      "location": "/Q{urn:oasis:names:specification:ubl:schema:xsd:Invoice-2}Invoice[1]/Q{urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2}LegalMonetaryTotal[1]",
      "ruleId": "BR-CO-16",
      "test": "xs:decimal(cbc:PayableAmount) = ...",
      "flag": "fatal",
      "role": null,
      "rawType": "FailedAssert"
    }
  ],
  "artifacts": {
    "persisted": true,
    "runId": "run-1234567890",
    "runDirectory": "/repo/prototypes/kosit-verification-mcp-service/target/runs/run-1234567890",
    "inputXmlPath": "/repo/prototypes/kosit-verification-mcp-service/target/runs/run-1234567890/input.xml",
    "resultJsonPath": "/repo/prototypes/kosit-verification-mcp-service/target/runs/run-1234567890/result.json",
    "reportXmlPath": "/repo/prototypes/kosit-verification-mcp-service/target/runs/run-1234567890/report.xml",
    "resources": {
      "inputXml": "xrechnung-run://run-1234567890/input.xml",
      "resultJson": "xrechnung-run://run-1234567890/result.json",
      "reportXml": "xrechnung-run://run-1234567890/report.xml"
    }
  },
  "rawKosit": {
    "acceptable": false,
    "wellformed": true,
    "schemaValid": true,
    "schematronValid": false,
    "acceptRecommendation": "REJECT",
    "processingErrors": [],
    "schemaViolations": [],
    "failedAsserts": [
      {
        "id": "BR-CO-16",
        "flag": "fatal",
        "location": "/Q{urn:oasis:names:specification:ubl:schema:xsd:Invoice-2}Invoice[1]/Q{urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2}LegalMonetaryTotal[1]"
      }
    ],
    "report": {
      "varlVersion": "1.0.0",
      "valid": false,
      "assessmentType": "reject"
    }
  }
}
```

## Toolfehler

Wenn das Tool technisch nicht sauber laufen konnte, bleibt der Envelope
grundsaetzlich erhalten, aber:

- `isError` im MCP-Tool-Result ist `true`
- `processingSuccessful` ist `false`
- `acceptRecommendation` ist `null`
- `toolError` beschreibt Kategorie, Nachricht und optionale Details

Beispiele fuer `toolError.category`:

- `input_invalid`
- `input_unreadable`
- `scope_unsupported`
- `artifact_write_failed`
- `unexpected`

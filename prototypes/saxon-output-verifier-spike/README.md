# Saxon Output Verifier Spike

Bewusst frueher, unvollstaendiger Spike fuer einen moeglichen
XRechnung-Verifier auf Basis von `Saxon-HE` direkt, also ohne KoSIT als
Laufzeit-Engine.

## Inhalt

- [Was dieser Spike ist](#was-dieser-spike-ist)
- [Nutzung und Build](#nutzung-von-bundle-docs)
- [Architekturentscheidungen im Code](#architekturentscheidungen-im-code)
- [Implementierung des Verifikationsprozesses](#implementierung-des-verifikationsprozesses)
- [Ergebnislogik](#ergebnislogik)
- [Update- und Maintenance-Folgen](#update--und-maintenance-folgen)

## Was dieser Spike ist

- eine Architektur- und Risiko-Skizze mit kleinem technischem Geruest
- ein Ort, um sichtbar zu machen, was ein Eigenbau wirklich nachbilden muesste
- ein Verifier fuer einen vorab festgelegten Ausgabeweg, nicht fuer beliebige
  legale Rechnungen
- ein bewusst kleiner, profilgesteuerter Ausgangsrechnungs-Verifier-Kern
- kein fertiger Ersatz fuer den Velocity Renderer oder den KoSIT Embedded
  Verifier

Diese README ist der technische Einstieg in Architektur, Code, offene Grenzen
und Maintenance-Aufwand bei kuenftigen Standard-Updates.

## Was der Spike aktuell kann

- die versionierte Validator-Konfiguration in `bundle-docs/` finden
- das ZIP lokal nach `target/validator-work/` entpacken
- ein kleines Profilregister mit `xrechnung-ubl-invoice` fuehren
- das vorab festgelegte UBL-Invoice-/XRechnung-Profil aus `scenarios.xml`
  gegen den Bundle-Stand pruefen
- ein XML als `UBL Invoice` inspizieren
- den Match-XPath dieses Profils mit Saxon als Profil-Gate auswerten
- die UBL-XSD mit JAXP validieren
- die beiden vorkompilierten UBL-Schematron-XSLTs mit Saxon ausfuehren
- rohe SVRL-Dateien in den Arbeitsordner schreiben
- einen klaren `PASS`-/`FAIL`-/`ERROR`-Befund auf der Konsole ausgeben
- ein maschinenlesbares JSON-Ergebnis in den Arbeitsordner schreiben

## Was der Spike bewusst noch nicht kann

- keine KoSIT-nahe `accept`-/`reject`-Entscheidung
- keine KoSIT-kompatible Report-XML-Erzeugung
- keinen HTML-Report
- keine VARL-kompatible Reporterzeugung
- keine allgemeine Dokumenterkennung ueber alle Dokumenttypen hinweg
- keine Nachbildung des KoSIT-Zwischenformats `in:createReportInput`
- kein `customLevel`-basiertes Assessment wie im KoSIT-Report-Weg
- keine Unterstuetzung fuer weitere Profile oder Dokumenttypen
- keine Behauptung von Ergebnisparitaet zum KoSIT-Reportformat

`AssessmentStage` markiert den Startpunkt fuer eine moegliche spaetere
KoSIT-naehere Report- und Assessment-Stufe. Diese Stufe ist in diesem Spike
bewusst nicht Teil des Verifier-Kerns.

## Bevorzugte Zielrichtung

Wenn aus diesem Spike spaeter ein echter Verifier wird, dann bevorzugt als
profilgesteuerter Pruefpfad fuer Ausgangsrechnungen:

- mit einem kleinen Profilparameter wie `--profile xrechnung-ubl-invoice`
- mit einem Default, der genau dem aktuell vom Repo ausgegebenen
  Rechnungstyp entspricht
- ohne automatische Dokumenterkennung im Standardpfad

Das Ziel waere dann nicht, jede beliebige legale Rechnung zu klassifizieren,
sondern immer genau das zu pruefen, was die Generatoren sowie Eingabe- und
Vorverarbeitungsschritte tatsaechlich ausgeben sollen.

Der aktuelle Code geht genau in diese Richtung:
`--profile` waehlt das erwartete Format, der Standardpfad bleibt fest auf den
bekannten Ausgangstyp zugeschnitten, und es gibt keine automatische
Dokumenterkennung im Normalbetrieb.

## Scope-Grenzen

Der aktuelle Scope ist bewusst eng:

- `XRechnung 3.0.2 / UBL Invoice`
- Default-Profil `xrechnung-ubl-invoice`
- keine KoSIT-Klassen
- kein HTTP-Aufruf
- kein CLI-Shell-out
- keine Unterstuetzung fuer `CreditNote`, `CII`, `Extension` oder `CVD`

## Nutzung von bundle-docs

Der Spike nutzt direkt die bestehende versionierte Datei
`bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`.

Es wird nichts in dieses Modul kopiert. Das ZIP wird nur lokal nach
`prototypes/saxon-output-verifier-spike/target/validator-work/` entpackt und daraus
gelesen.

## Build

Vom Projektwurzelverzeichnis aus:

```bash
mvn -f prototypes/saxon-output-verifier-spike/pom.xml package
```

Das erzeugt ein lauffaehiges Fat-JAR:

```bash
prototypes/saxon-output-verifier-spike/target/saxon-output-verifier-spike.jar
```

## Beispielaufruf

```bash
java -jar prototypes/saxon-output-verifier-spike/target/saxon-output-verifier-spike.jar \
  --xml /tmp/invoice-full.xml \
  --profile xrechnung-ubl-invoice
```

Ohne `--profile` wird standardmaessig `xrechnung-ubl-invoice` verwendet.

Der Lauf schreibt:

- rohe SVRL-Ausgaben unter
  `prototypes/saxon-output-verifier-spike/target/validator-work/svrl/`
- ein JSON-Ergebnis unter
  `prototypes/saxon-output-verifier-spike/target/validator-work/results/`

Optional kann der JSON-Pfad explizit gesetzt werden:

```bash
java -jar prototypes/saxon-output-verifier-spike/target/saxon-output-verifier-spike.jar \
  --xml /tmp/invoice-full.xml \
  --result-json /tmp/saxon-verifier-result.json
```

## Architekturentscheidungen im Code

Die Architekturentscheidungen sind hier bewusst an konkrete Klassen gebunden,
damit der Spike nicht nur konzeptionell, sondern auch im Code nachvollziehbar
bleibt:

| Entscheidung | Im Code umgesetzt durch |
|---|---|
| A-priori-Profil statt Autodetection | `--profile`, [`ProfileRegistry`](./src/main/java/local/xrechnung/saxonoutputverifier/ProfileRegistry.java), [`ExpectedProfile`](./src/main/java/local/xrechnung/saxonoutputverifier/ExpectedProfile.java) und den gezielten Zugriff auf genau ein erwartetes Szenario |
| Profilvertrag im Code | [`ExpectedProfile.validateScenarioDefinition(...)`](./src/main/java/local/xrechnung/saxonoutputverifier/ExpectedProfile.java), das XSD-Pfad, Schematron-Reihenfolge und Report-XSLT gegen `scenarios.xml` prueft |
| Bundle-Artefakte direkt nutzen | [`ValidatorBundleSupport.prepare(...)`](./src/main/java/local/xrechnung/saxonoutputverifier/ValidatorBundleSupport.java), das das ZIP aus `bundle-docs/` findet und lokal nach `target/validator-work/` entpackt |
| Zwei Engines statt Saxon-only | [`SchemaValidationStage`](./src/main/java/local/xrechnung/saxonoutputverifier/SchemaValidationStage.java) fuer JAXP-XSD und [`SaxonSchematronStage`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonSchematronStage.java) fuer Saxon-HE gegen vorkompilierte Schematron-XSLTs |
| Eigene Verifier-Semantik | [`SaxonVerifier`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonVerifier.java), [`VerificationVerdict`](./src/main/java/local/xrechnung/saxonoutputverifier/VerificationVerdict.java) und die Trennung zwischen `PASS`, `FAIL` und `ERROR` |
| Rohes, maschinenlesbares Ergebnis | [`VerificationResult`](./src/main/java/local/xrechnung/saxonoutputverifier/VerificationResult.java), [`VerifierFinding`](./src/main/java/local/xrechnung/saxonoutputverifier/VerifierFinding.java), [`ResultJsonWriter`](./src/main/java/local/xrechnung/saxonoutputverifier/ResultJsonWriter.java) und rohe SVRL-Artefakte |

## Implementierung des Verifikationsprozesses

Der Code ist absichtlich als kleine Pipeline gebaut. Der zentrale Einstieg ist
[`SaxonOutputVerifierCli`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonOutputVerifierCli.java):

1. `CliArguments.parse(...)` liest `--xml`, `--profile`, `--work-dir` und
   `--result-json`.
2. `RepositoryLocator.findProjectRoot()` sucht den Repo-Root mit
   `bundle-docs/` und `prototypes/saxon-output-verifier-spike/`.
3. `ProfileRegistry.builtInProfiles().require(...)` loest das erwartete
   Ausgabeprofil auf. Das verwirklicht die Entscheidung fuer ein
   A-priori-Profil statt Autodetection. Aktuell gibt es nur
   `xrechnung-ubl-invoice`.
4. `SaxonVerifier.verify(...)` fuehrt die eigentliche Pruefpipeline aus.
5. `ResultJsonWriter.write(...)` schreibt das maschinenlesbare Ergebnis.
6. `SaxonOutputVerifierCli` druckt eine kurze Zusammenfassung und setzt den
   Exit-Code: `0` fuer `PASS`, `1` fuer `FAIL`, `2` fuer `ERROR`.

Die eigentliche Pipeline liegt in
[`SaxonVerifier.verify(...)`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonVerifier.java):

1. `ValidatorBundleSupport.prepare(...)`
   findet `bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`,
   entpackt es bei Bedarf nach `target/validator-work/` und liefert den Pfad
   zu `scenarios.xml`. Damit wird die Entscheidung umgesetzt, Bundle-Artefakte
   direkt zu nutzen und nichts in das Modul zu kopieren.
2. `ScenarioCatalog.load(...)` liest `scenarios.xml`.
   `requireScenario(...)` holt daraus nur das erwartete Szenario
   `EN16931 XRechnung (UBL Invoice)`. Das haelt die Szenarioauswahl bewusst
   profilgebunden.
3. `ExpectedProfile.validateScenarioDefinition(...)` prueft, ob das Bundle noch
   zu unserem fest verdrahteten Profilvertrag passt: XSD-Pfad,
   Schematron-Reihenfolge und Report-XSLT muessen stimmen.
4. `InvoiceInspector.inspect(...)` liest Root-Element, `CustomizationID` und
   `ProfileID` aus der Eingabe-XML.
5. `ScenarioMatcher.matches(...)` wertet den Match-XPath des Szenarios mit
   Saxon-HE aus.
6. `ProfileGate.evaluate(...)` kombiniert die statischen Erwartungen aus
   `ExpectedProfile`, die XML-Kopfdaten aus `InvoiceInspector` und das
   XPath-Ergebnis aus `ScenarioMatcher`.
7. Wenn das Profil-Gate nicht passt, endet der Lauf mit `FAIL`; XSD und
   Schematron werden dann nicht ausgefuehrt.
8. `SchemaValidationStage.validate(...)` prueft die XML mit JAXP gegen das
   UBL-Invoice-XSD aus dem entpackten Bundle. Das ist die XSD-Haelfte der
   Zwei-Engines-Entscheidung.
9. Wenn die XSD-Pruefung fehlschlaegt, endet der Lauf mit `FAIL`; die
   Schematron-Stufen werden dann nicht ausgefuehrt.
10. `SaxonSchematronStage.run(...)` fuehrt die vorkompilierten
    Schematron-XSLTs in der Reihenfolge aus `scenarios.xml` aus und schreibt
    pro Stufe eine rohe SVRL-Datei. Das ist die Saxon-HE-Haelfte der
    Zwei-Engines-Entscheidung.
11. `SaxonVerifier` zaehlt die SVRL-`failed-assert`s. Keine Failed Asserts
    ergeben `PASS`, mindestens ein Failed Assert ergibt `FAIL`. Hier wird die
    eigene Verifier-Semantik umgesetzt, nicht eine KoSIT-nahe
    `accept`-/`reject`-Logik.

Technische Fehler wie fehlendes Bundle, nicht lesbare XML, kaputte
Transformationen oder unerwartete Exceptions werden als `ERROR` modelliert.
Fachlich ungueltige Rechnungen sind dagegen normale `FAIL`-Ergebnisse.

## Zentrale Klassen

| Klasse | Rolle |
|---|---|
| [`SaxonOutputVerifierCli`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonOutputVerifierCli.java) | CLI-Einstieg, Exit-Codes, Konsolenausgabe |
| [`CliArguments`](./src/main/java/local/xrechnung/saxonoutputverifier/CliArguments.java) | Argument-Parsing und Default-Pfade |
| [`ExpectedProfile`](./src/main/java/local/xrechnung/saxonoutputverifier/ExpectedProfile.java) | Vertrag fuer den erwarteten Ausgangsrechnungstyp: Root-QName, `CustomizationID`, `ProfileID`, XSD, Schematron-Reihenfolge, Report-XSLT |
| [`ProfileRegistry`](./src/main/java/local/xrechnung/saxonoutputverifier/ProfileRegistry.java) | kleines Register fuer bekannte Profile; aktuell nur `xrechnung-ubl-invoice` |
| [`ValidatorBundleSupport`](./src/main/java/local/xrechnung/saxonoutputverifier/ValidatorBundleSupport.java) | findet und entpackt die Validator-Konfiguration aus `bundle-docs/` |
| [`ScenarioCatalog`](./src/main/java/local/xrechnung/saxonoutputverifier/ScenarioCatalog.java) und [`ScenarioDefinition`](./src/main/java/local/xrechnung/saxonoutputverifier/ScenarioDefinition.java) | lesen den relevanten Ausschnitt aus `scenarios.xml` |
| [`InvoiceInspector`](./src/main/java/local/xrechnung/saxonoutputverifier/InvoiceInspector.java) | liest die XML-Kopfdaten fuer das Profil-Gate |
| [`ScenarioMatcher`](./src/main/java/local/xrechnung/saxonoutputverifier/ScenarioMatcher.java) | wertet den Bundle-Match-XPath mit Saxon-HE aus |
| [`ProfileGate`](./src/main/java/local/xrechnung/saxonoutputverifier/ProfileGate.java) | entscheidet, ob die Eingabe zum erwarteten Profil passt |
| [`SchemaValidationStage`](./src/main/java/local/xrechnung/saxonoutputverifier/SchemaValidationStage.java) | fuehrt die XSD-Pruefung mit JAXP aus und erzeugt `xsd`-Findings |
| [`SaxonSchematronStage`](./src/main/java/local/xrechnung/saxonoutputverifier/SaxonSchematronStage.java) | fuehrt die Schematron-XSLTs mit Saxon-HE aus, schreibt SVRL und extrahiert `schematron`-Findings |
| [`VerificationResult`](./src/main/java/local/xrechnung/saxonoutputverifier/VerificationResult.java), [`VerifierFinding`](./src/main/java/local/xrechnung/saxonoutputverifier/VerifierFinding.java) und [`VerificationVerdict`](./src/main/java/local/xrechnung/saxonoutputverifier/VerificationVerdict.java) | internes Ergebnis- und Findings-Modell |
| [`ResultJsonWriter`](./src/main/java/local/xrechnung/saxonoutputverifier/ResultJsonWriter.java) | serialisiert das Ergebnis als JSON |
| [`AssessmentStage`](./src/main/java/local/xrechnung/saxonoutputverifier/AssessmentStage.java) | bewusst noch nicht integrierte Notiz zur KoSIT-nahen Report-/Assessment-Stufe |

## Ergebnislogik

Der Verifier unterscheidet bewusst technische Verarbeitung und fachliche
Pruefung:

| Zustand | Verdict | `processingSuccessful` | Danach |
|---|---|---|---|
| Profil-Gate passt nicht | `FAIL` | `true` | keine XSD- oder Schematron-Pruefung |
| XSD hat Fehler | `FAIL` | `true` | keine Schematron-Pruefung |
| Schematron hat mindestens ein `failed-assert` | `FAIL` | `true` | SVRL und JSON werden geschrieben |
| Profil, XSD und Schematron sind sauber | `PASS` | `true` | SVRL und JSON werden geschrieben |
| Bundle, XML, XSD-Engine oder Saxon-Lauf scheitert technisch | `ERROR` | `false` | JSON enthaelt `toolError` |

Die Findings bleiben roh und maschinennah:

- `custom` fuer Profil-Gate-Mismatches
- `xsd` fuer JAXP-XSD-Meldungen
- `schematron` fuer SVRL-`failed-assert` und `successful-report`

## Aenderungspunkte

| Aufgabe | Zentrale Stelle |
|---|---|
| weiteres festes Ausgabeprofil hinzufuegen | `ExpectedProfile` und `ProfileRegistry` |
| Profil-Gate strenger oder lockerer machen | `ProfileGate` und `InvoiceInspector` |
| Bundle- oder `scenarios.xml`-Auswertung anpassen | `ValidatorBundleSupport`, `ScenarioCatalog`, `ScenarioDefinition` |
| XSD-Fehler anders abbilden | `SchemaValidationStage` |
| Schematron-/SVRL-Findings anders auswerten | `SaxonSchematronStage` |
| JSON-Vertrag erweitern | `VerificationResult`, `VerifierFinding`, `ResultJsonWriter` und [`docs/result-format.md`](./docs/result-format.md) |
| KoSIT-naehere Accept-/Reject-Semantik bauen | `AssessmentStage` als Startpunkt, plus neues Zwischenmodell fuer `in:createReportInput` |

Das Ergebnisformat bleibt bewusst klein und maschinenlesbar. Es enthaelt unter
anderem:

- `schemaVersion`
- `toolVersion`
- `profile`
- `input`
- `processingSuccessful`
- `verificationVerdict`
- `summary`
- `findings`
- `artifacts`
- `rawVerifier`
- optional `toolError`

Eine kurze Feldbeschreibung steht in
[`docs/result-format.md`](./docs/result-format.md).

## Update- und Maintenance-Folgen

Bei jedem kleinen Standard- oder Bundle-Update muss mindestens geprueft werden:

- ob Szenarioname, Match-XPath, Namespaces und Ressourcenpfade noch zum Profil
  passen
- ob XSD-Pfad und Schematron-Reihenfolge unveraendert oder bewusst angepasst
  sind
- ob die vorkompilierten XSLTs weiter mit der gepflegten Saxon-Version laufen
- ob SVRL-Struktur, Findings und JSON-Ergebnis unveraendert interpretierbar
  bleiben

Bei groesseren Updates kommen hinzu:

- Profilvertrag und Scope-Grenzen neu bewerten
- moegliche neue Dokumenttypen oder Profile bewusst ein- oder ausschliessen
- Report- und Assessment-Semantik erneut gegen die Bundle-Konfiguration
  abgleichen
- klaeren, ob das Bundle weiterhin lauffaehige XSLTs liefert oder ob eine
  `.sch`-zu-XSLT-Stufe gepflegt werden muesste

Der dauerhafte Aufwand liegt nicht im blossen Ausfuehren von XSLT. Teuer sind
Profilvertrag, XSD-Engine-Entscheidung, Schematron-Kette,
Severity-Uebersteuerungen, Report-Semantik und Update-Regressionen.

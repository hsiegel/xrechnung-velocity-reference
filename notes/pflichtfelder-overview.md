# Pflichtfelder in XRechnung CIUS 3.0.2

Auswertung des offiziellen SeMoX-Modells fuer `invoice`.

## Quellen

- `bundle-docs/xrechnung/model/semox/xrechnung-cius-model.xml`
- `bundle-docs/xrechnung/model/README.md`
- `bundle-docs/xrechnung/testsuite/instances/technical-cases/cius/01.05_minimal_test_ubl.xml`

## Methodik

1. Strukturell global verpflichtend:
   `min-occurs != 0` und kein optionaler Vorfahre im `invoice`-Baum.
2. Strukturell nur innerhalb optionaler Gruppen verpflichtend:
   `min-occurs != 0`, aber mindestens ein optionaler Vorfahre.
3. Regelbasiert verpflichtend:
   Pflichtigkeit entsteht erst durch Business Rules, Alternative-oder-Regeln oder
   spezifische Steuerkategorien.

## Kurzfazit

- Das Modell enthaelt `164` Business Terms (`BT-*`) und `32` Gruppen (`BG-*`).
- Davon sind `36` Terms und `13` Gruppen global strukturell verpflichtend.
- Zusaetzlich gibt es `23` Terms, die innerhalb optionaler Gruppen zwingend werden.
- In den Regeltexten finden sich `107` Muss-Regeln mit Pflichtigkeitscharakter.
- Jenseits der globalen Struktur tauchen `43` unterschiedliche `BT-*` in
  Pflichtigkeitsregeln auf.

## Einordnung

- Die Pflichtfeldlisten eignen sich gut fuer Diagnose und Analyse.
- Ein vollstaendiger Vorab-Check fuer alle konditionalen XRechnung-Pflichten ist
  bereits ein kleiner Fachvalidator und widerspricht schnell dem Ziel, den
  Standard moeglichst nur ueber Template-Updates nachzuziehen.

## 1. Global strukturell verpflichtend

Diese Felder gelten fuer jede Rechnung, unabhaengig vom konkreten Geschaeftsfall.

### Kopf

- `BT-1` Invoice number
- `BT-2` Issue date
- `BT-3` Invoice type code
- `BT-5` Document currency code
- `BT-10` Buyer reference

### Prozesskennungen

- `BG-2`
- `BT-23` Business process type identifier
- `BT-24` Specification identifier

### Verkaeufer

- `BG-4`
- `BG-5`
- `BG-6`
- `BT-27` Seller name
- `BT-34` Seller electronic address
- `BT-37` Seller city
- `BT-38` Seller post code
- `BT-40` Seller country code
- `BT-41` Seller contact point
- `BT-42` Seller contact telephone number
- `BT-43` Seller contact email address

### Erwerber

- `BG-7`
- `BG-8`
- `BT-44` Buyer name
- `BT-49` Buyer electronic address
- `BT-52` Buyer city
- `BT-53` Buyer post code
- `BT-55` Buyer country code

### Zahlung

- `BG-16`
- `BT-81` Payment means type code

### Summen

- `BG-22`
- `BT-106` Sum of invoice line net amount
- `BT-109` Invoice total amount without VAT
- `BT-112` Invoice total amount with VAT
- `BT-115` Amount due for payment

### Umsatzsteueraufschluesselung

- `BG-23` mindestens einmal
- `BT-116` VAT category taxable amount
- `BT-117` VAT category tax amount
- `BT-118` VAT category code
- `BT-119` VAT category rate

### Rechnungsposition

- `BG-25` mindestens einmal
- `BG-29`
- `BG-30`
- `BG-31`
- `BT-126` Invoice line identifier
- `BT-129` Invoiced quantity
- `BT-130` Invoiced quantity unit code
- `BT-131` Invoice line net amount
- `BT-146` Item net price
- `BT-151` Invoiced item VAT category code
- `BT-153` Item name

## 2. Innerhalb optionaler Gruppen verpflichtend

Diese Felder sind nicht fuer jede Rechnung noetig, aber sobald der jeweilige Block
verwendet wird, sind sie hart erforderlich.

| Optionale Gruppe | Dann verpflichtend |
|---|---|
| `BG-1` Additional supporting documents / Notes | `BT-22` |
| `BG-3` Preceding invoice reference | `BT-25` |
| `BG-10` Payee | `BT-59` |
| `BG-11` Seller tax representative | `BT-62`, `BT-63`, `BG-12` |
| `BG-12` Tax representative postal address | `BT-69` |
| `BG-15` Deliver to address | `BT-77`, `BT-78`, `BT-80` |
| `BG-17` Payment account information | `BT-84` |
| `BG-18` Card information | `BT-87` |
| `BG-19` Direct debit | `BT-89`, `BT-90`, `BT-91` |
| `BG-20` Document level allowance | `BT-92`, `BT-95` |
| `BG-21` Document level charge | `BT-99`, `BT-102` |
| `BG-24` Supporting document | `BT-122` |
| `BG-27` Invoice line allowance | `BT-136` |
| `BG-28` Invoice line charge | `BT-141` |
| `BG-32` Item attribute | `BT-160`, `BT-161` |

Hinweis:

- Diese Kategorie ist fuer eine Vorvalidierung sehr dankbar.
- Sie laesst sich nahezu rein aus Baumstruktur und Listenlogik abfangen.

## 3. Rein regelbasierte Pflichtigkeiten

Diese Pflichten stehen nicht einfach als `1..1` im Strukturbaum, sondern entstehen
durch Inhalte, Kombinationen oder Alternativen.

### Zahlung und Faelligkeit

- `BT-9` oder `BT-20`:
  Bei positivem `BT-115` muss entweder Faelligkeitsdatum oder Zahlungsbedingung
  vorhanden sein (`BR-CO-25`).
- `BT-84`:
  Pflicht bei Ueberweisungsinformationen bzw. bestimmten `BT-81`-Codes
  (`BR-50`, `BR-61`).
- `BT-111`:
  Pflicht, wenn `BT-6` gesetzt ist (`BR-53`).

### Elektronische Adressen und Identifier-Komponenten

- `BT-34` braucht `schemeID` (`BR-62`).
- `BT-49` braucht `schemeID` (`BR-63`).
- `BT-157` braucht `schemeID` (`BR-64`).
- `BT-158` braucht `schemeID` (`BR-65`).

### Identifikation des Verkaeufers / Erwerbers

- Mindestens eines aus `BT-29`, `BT-30`, `BT-31` muss fuer die automatische
  Lieferantenidentifikation vorhanden sein (`BR-CO-26`).
- Fuer bestimmte Steuerkategorien werden weitere Steuerkennungen verpflichtend:
  `BT-31`, `BT-32`, `BT-63`, teilweise auch `BT-47` und `BT-48`.

### Alternative Pflichtpaare fuer Zuschlaege / Nachlaesse

- `BG-20`: `BT-97` oder `BT-98` (`BR-33`, `BR-CO-21`)
- `BG-21`: `BT-104` oder `BT-105` (`BR-38`, `BR-CO-22`)
- `BG-27`: `BT-139` oder `BT-140` (`BR-42`, `BR-CO-23`)
- `BG-28`: `BT-144` oder `BT-145` (`BR-44`, `BR-CO-24`)

### Steuerkategorie-getriebene Pflichten

Die groessten Zusatzaufwaende kommen aus den Umsatzsteuer-Regelgruppen:

- `Standard rated`, `Zero rated`, `Exempt`, `IGIC`, `IPSI`
  fuehren typischerweise zu Pflichten auf `BT-31`, `BT-32` und/oder `BT-63`.
- `Reverse charge`
  fuehrt zusaetzlich zu Pflichten auf Erwerberseite:
  `BT-48` und/oder `BT-47`.
- `Intra-community supply`
  fuehrt typischerweise zu `BT-31` oder `BT-63` sowie `BT-48`.
- `Exempt`, `Reverse charge`, `Intra-community`, `Export outside the EU`,
  `Not subject to VAT`
  fuehren in `BG-23` zu Pflicht auf `BT-120` oder `BT-121`.
- Viele Kategorien verlangen ausserdem, dass in `BG-23` der passende
  `BT-118`-Code ueberhaupt vorhanden ist.

### Nationale / sonstige Zusatzpflichten

- `BG-16` ist in den nationalen Regeln nochmals explizit verpflichtend (`BR-DE-1`).
- Bei Anhaengen in `BT-125` wird ein eindeutiger Dateiname gefordert (`BR-DE-22`).

## 4. Aufwandseinschaetzung fuer Diagnose- oder Vorab-Checks

### Stufe A: Render-Vorcheck

Prueft nur, was das Template fuer die Kernstruktur braucht.

Umfang:

- globale Pflichtfelder aus Abschnitt 1
- Listen-Mindestanzahl fuer `BG-23` und `BG-25`
- `currencyID`, `unitCode`, `schemeID` fuer bereits benutzte Felder

Einschaetzung:

- relativ ueberschaubar
- gut als Diagnose-Check oder optionaler Hinweis-Check machbar

### Stufe B: optionale Gruppen

Prueft, ob optional geoeffnete Gruppen ihre lokalen Pflichtfelder enthalten.

Umfang:

- Tabelle aus Abschnitt 2
- einfache Oder-Regeln fuer Nachlass-/Abgabegruende

Einschaetzung:

- immer noch gut beherrschbar
- gut geeignet fuer eine spaetere Diagnoseliste "welche Stellen fehlen noch"

### Stufe C: fachliche Vollpruefung

Prueft auch steuerkategoriegetriebene Pflichtigkeit, Alternativen,
Identifikationsregeln und Sonderfaelle.

Umfang:

- grosse Teile der Regeln aus Abschnitt 3
- starke Naehe zu einem Fachvalidator

Einschaetzung:

- deutlich groesserer Aufwand
- hier lohnt sich meist ein gestuftes Vorgehen statt alles in einem Schritt vor
  dem Rendering nachzubauen

## Empfehlung

Fuer den Draft-Anwendungsfall ist ein toleranter Renderer die passendere
Grundhaltung:

1. Die `.vm` rendert vorhandene Inhalte und laesst Fehlendes einfach weg.
2. Pflichtigkeiten werden nicht als Render-Schranke verwendet.
3. Die hier ermittelten Pflichtfelder und Regeln dienen danach fuer Diagnose,
   Verifikation und Nutzerhinweise.

So bleibt die Anpassungsflaeche bei Standardaenderungen in Template- und
Mapping-Ebene. Falls spaeter Vorab-Pruefungen gewuenscht sind, sollten sie mit
diagnostischen Hinweisen beginnen und nicht als harte Vorbedingung fuer das
Rendering.

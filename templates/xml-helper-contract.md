# `$xml` Helper Contract fuer Velocity

Minimaler Helper-Vertrag fuer die Velocity-Templates.

Verwendet in:

- [ubl-invoice-core.vm](./ubl-invoice-core.vm)
- [ubl-invoice-full.vm](./ubl-invoice-full.vm)

Java-Referenz:

- [XmlHelper.java](../prototypes/velocity-renderer/src/main/java/local/xrechnung/velocityrenderer/XmlHelper.java)

## Benoetigte Methoden

Die Templates erwarten diese Methoden:

- `has(Object value): boolean`
- `text(Object value): String`
- `attr(Object value): String`
- `date(Object value): String`
- `amount(Object value): String`
- `number(Object value): String`

## Minimale Java-Form

Zum Beispiel so:

```java
public interface XmlHelper {
  boolean has(Object value);
  String text(Object value);
  String attr(Object value);
  String date(Object value);
  String amount(Object value);
  String number(Object value);
}
```

Fuer Velocity ist ein einfaches Objekt mit oeffentlichen Methoden und diesen
Namen am einfachsten.

## Semantik pro Methode

### `has`

Zweck:

- Presence-Check fuer alle optionalen Werte, Attribute, Listen und Gruppen.

Muss gelten:

- `null` -> `false`
- leerer String -> `false`
- Blank-String wie `"   "` -> `false`
- numerische `0` -> `true`
- `BigDecimal.ZERO` -> `true`
- negative Zahlen -> `true`
- `false` als Boolean sollte, falls jemals verwendet, als vorhandener Wert
  gelten, also `true`
- leere Collection / leeres Array / leere Map -> `false`
- Collection / Array nur dann `true`, wenn rekursiv mindestens ein Eintrag
  wirklich Inhalt hat
- Map nur dann `true`, wenn rekursiv mindestens ein Value wirklich Inhalt hat
- eine Map mit nur `null`, Blank-Strings, leeren Listen oder leeren Maps ist
  also `false`
- komplexe Objekte / Beans ausserhalb von Collection/Map/Array duerfen weiter
  als `non-null` gelten

Hinweis:

- Die Templates verlassen sich weiterhin nicht auf tiefe Bean-Inspektion.
- Fuer echte Objekte reicht im Regelfall `value != null`.
- Fuer Maps, Arrays und Collections lohnt sich aber eine rekursive
  Presence-Pruefung, damit null-gefuellte Adapter-Objekte nicht versehentlich
  Wrapper aufmachen.

Beispiele:

- `has("")` muss `false` sein, weil z. B. die Core-Vorlage absichtlich `""`
  an `xrTaxTotal(..., "", ...)` uebergibt, damit der zweite `TaxAmount`
  sicher unterdrueckt wird.
- `has({ price: { netAmount: null } })` sollte `false` sein, wenn diese Map
  am Ende nur leere Teilwerte enthaelt.
- `has([{ id: null }, { id: "A1" }])` sollte `true` sein, weil die Liste
  rekursiv einen echten Eintrag enthaelt.

### `text`

Zweck:

- XML-escaped Elementinhalt fuer Text-, Code- und Identifier-Werte.

Muss gelten:

- Rueckgabe ist XML-sicher fuer Elementinhalt
- mindestens `&`, `<` und `>` muessen escaped werden
- sollte defensiv auch mit `"` und `'` sauber umgehen koennen
- ungueltige XML-1.0-Zeichen sollten entfernt oder anderweitig sanitisiert
  werden, statt den Renderlauf hart abzubrechen
- keine Business-Defaults bilden
- keine Datums- oder Zahlenformatierung machen
- `null` sollte defensiv zu `""` werden duerfen, auch wenn die Templates das
  normalerweise schon ueber `has` abfangen

### `attr`

Zweck:

- XML-escaped Attributwert, etwa fuer `schemeID`, `currencyID`, `unitCode`,
  `name`, `mimeCode`, `filename`.

Muss gelten:

- Rueckgabe ist XML-sicher fuer Attributkontext
- mindestens `&`, `<`, `>`, `"` und `'` muessen escaped werden
- ungueltige XML-1.0-Zeichen sollten entfernt oder anderweitig sanitisiert
  werden, statt den Renderlauf hart abzubrechen
- keine Business-Defaults bilden
- `null` darf defensiv zu `""` werden

### `date`

Zweck:

- Ausgabe von Datumswerten im XML-Format `YYYY-MM-DD`.

Muss gelten:

- Ausgabeformat exakt `yyyy-MM-dd`
- keine lokalisierte Ausgabe
- keine Zeitkomponente
- keine implizite Zeitzonenlogik
- empfohlen: nur bereits date-artige Werte akzeptieren, z. B. `LocalDate`
  oder einen schon normalisierten String

Hinweis:

- Falls intern `LocalDateTime`, `Instant` oder `Date` vorkommen, sollte die
  Umwandlung zur reinen Rechnungssicht vorher passieren und nicht heimlich in
  `date()`.

### `amount`

Zweck:

- Formatierte Dezimaldarstellung fuer Geldbetraege.

Muss gelten:

- Dezimalpunkt `.` statt lokaler Kommaschreibweise
- keine Tausendertrennzeichen
- keine wissenschaftliche Notation wie `1E+2`
- kein stilles Runden
- negative Werte muessen moeglich sein
- `0` muss als echter Wert ausgebbar sein

Empfehlung:

- Werte sollten vorher bereits fachlich normalisiert und gerundet sein
- `BigDecimal.toPlainString()` passt hier gut
- `Double`/`Float` sollten nicht still unpraezise durchrutschen

### `number`

Zweck:

- Formatierte Dezimaldarstellung fuer Mengen, Prozentwerte und sonstige
  numerische Nicht-Geldwerte.

Muss gelten:

- gleiche Formatregeln wie bei `amount`
- Dezimalpunkt `.`
- keine Tausendertrennzeichen
- keine wissenschaftliche Notation
- kein stilles Runden
- `0` muss ausgebbar sein

Hinweis:

- Implementierung darf technisch dieselbe Formatlogik wie `amount()` nutzen
- der getrennte Name haelt die Templates semantisch lesbar

## Was `$xml` nicht tun sollte

- keine Pflichtfeldpruefung
- keine XRechnung-Geschaeftsregeln
- keine Summenbildung
- keine Steueraufschluesselung
- keine Defaultwerte fuer fehlende Fachdaten
- keine tiefe UBL-spezifische Logik

Das gehoert in die Voraufbereitung oder spaetere Validierung, nicht in den
Render-Helper.

## Implementierung

Sinnvolle Zusatzregeln:

- Optional/Optionals intern auspacken, falls sie doch in den Kontext geraten
- Arrays wie Collections behandeln
- fuer Zahlen moeglichst ueber `BigDecimal` normalisieren
- fuer `text()` und `attr()` eine zentrale XML-Escape-Funktion benutzen
- fuer XML-Escaping ist `StringEscapeUtils.escapeXml10()` aus
  `commons-text` ein sinnvoller Ausgangspunkt

## Kurzfassung

Wenn `$xml` diese sechs Dinge sauber kann, reichen die aktuellen Templates aus:

- Presence pruefen: `has`
- Elementtext escapen: `text`
- Attributwerte escapen: `attr`
- Datum formatieren: `date`
- Betraege formatieren: `amount`
- sonstige Zahlen formatieren: `number`

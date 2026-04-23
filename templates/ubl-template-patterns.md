# Template Patterns fuer UBL/XRechnung

## Grundlage

- Grundlage ist das lokale Bundle in
  [README.md](../bundle-docs/xrechnung/bundle/README.md).
- Spezifikation: `XRechnung Specification 3.0.2`.
- Syntax-Binding: `zu 3.0.2`.
- Verwendetes SeMoX-Modell:
  `bundle-docs/xrechnung/model/semox/xrechnung-cius-model.xml` aus dem Model-Stand
  `2026-01-31`.
- Fokus hier ist `UBL Invoice` (`ubl-inv`).

## Aufbau

- Aenderungen am Standard moeglichst in `yaml` und `.vm` halten.
- Wiederkehrende UBL-Strukturen nicht immer wieder neu schreiben.
- Das semantische Rechnungsmodell unter `xr` stabil halten, auch wenn sich
  vorgelagerte Datenquellen oder Mapping-Schritte aendern.
- Die eigentliche `Invoice.vm` auf eine duenne Dokument-Schale reduzieren.

Blockuebersicht:
[xrechnung-3.0.2-ubl-invoice-block-overview.md](../semantic-model/xrechnung-3.0.2-ubl-invoice-block-overview.md).

## Schichten

Die Templates trennen drei Schichten:

1. Primitive Render-Muster
   Werte, Attribute, Identifier, Amounts, Quantity, Date.
2. UBL-Bausteine
   Address, Contact, Party, Delivery, PaymentMeans, TaxCategory,
   AllowanceCharge, AdditionalDocumentReference, InvoiceLine.
3. Dokumentkomposition
   Die eigentliche `Invoice`, die nur noch Makros zusammensetzt.

## Pattern-Katalog

| Pattern | Typische BT/BG | UBL-Form | Macro |
|---|---|---|---|
| Einfacher Wert | `BT-1`, `BT-10`, `BT-23` | `cbc:*` | `xrText`, `xrDate`, `xrNumber` |
| Wert mit Sonderattribut | `BT-82` | `cbc:* @name` | `xrPaymentMeans` |
| Wert mit `schemeID` | `BT-34`, `BT-49`, `BT-157` | `cbc:* @schemeID` | `xrIdentifier` |
| Wert mit `currencyID` | `BT-92`, `BT-106`, `BT-117` | `cbc:* @currencyID` | `xrAmount` |
| Wert mit `unitCode` | `BT-129`, `BT-149` | `cbc:* @unitCode` | `xrQuantity` |
| Notiz mit Subject Code | `BG-1`, `BT-21`, `BT-22` | `cbc:Note` | `xrNote` |
| Rechnungszeitraum | `BG-14`, `BG-26`, `BT-73/74`, `BT-134/135` | `cac:InvoicePeriod` | `xrInvoicePeriod` |
| Adresse | `BG-5`, `BG-8`, `BG-12`, `BG-15` | `cac:PostalAddress` oder `cac:Address` | `xrAddress`, `xrPostalAddress` |
| Kontakt | `BG-6`, `BG-9` | `cac:Contact` | `xrContact` |
| Partei mit `cac:Party` | `BG-4`, `BG-7` | `cac:Accounting*Party/cac:Party` | `xrNestedParty` |
| Flache Partei | `BG-10`, `BG-11` | `cac:PayeeParty`, `cac:TaxRepresentativeParty` | `xrFlatParty` |
| Einfache Dokumentreferenz | `BT-12`, `BT-15`, `BT-16`, `BT-17` | `cac:*Reference/cbc:ID` | `xrDocumentReference` |
| Projektreferenz | `BT-11` | `cac:ProjectReference/cbc:ID` | `xrProjectReference` |
| Bestellreferenz | `BT-13`, `BT-14` | `cac:OrderReference` | `xrOrderReference` |
| Vorherige Rechnung | `BG-3`, `BT-25`, `BT-26` | `cac:BillingReference/cac:InvoiceDocumentReference` | `xrBillingReference` |
| Rechnungsobjekt-Referenz | `BT-18` | `cac:AdditionalDocumentReference` mit `DocumentTypeCode=130` | `xrInvoicedObjectReference` |
| Lieferblock | `BG-13`, `BG-15`, `BT-70..80` | `cac:Delivery` | `xrDelivery` |
| Zahlungsblock | `BG-16..19`, `BT-81..91` | `cac:PaymentMeans` | `xrPaymentMeans`, `xrPaymentTerms` |
| Nachlass/Zuschlag | `BG-20/21/27/28`, `BT-92..105`, `BT-136..145` | `cac:AllowanceCharge` | `xrAllowanceCharge` |
| Steuerkategorie | `BT-95/96`, `BT-102/103`, `BT-118..121`, `BT-151/152` | `cac:TaxCategory`, `cac:ClassifiedTaxCategory` | `xrTaxCategory` |
| Umsatzsteueraufschluesselung | `BG-23`, `BT-110/111`, `BT-116..121` | `cac:TaxTotal/cac:TaxSubtotal` | `xrTaxTotal` |
| Dokumentensumme | `BG-22`, `BT-106..115` | `cac:LegalMonetaryTotal` | `xrMonetaryTotal` |
| Rechnungsbegruendende Unterlage | `BG-24`, `BT-122..125` | `cac:AdditionalDocumentReference` | `xrAdditionalDocumentReference` |
| Positionsobjekt-Referenz | `BT-128` | `cac:InvoiceLine/cac:DocumentReference` mit `DocumentTypeCode=130` | `xrLineObjectReference` |
| Preisdetails | `BG-29`, `BT-146..150` | `cac:Price` | `xrPrice` |
| Artikelinformationen | `BG-30..32`, `BT-151..161` | `cac:Item` | `xrItem` |
| Rechnungsposition | `BG-25`, `BT-126..161` | `cac:InvoiceLine` | `xrInvoiceLine` |

## Beispielhafte Modellformen

### Kopf und einfache Referenzen

```yaml
process:
  customizationId: urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0
  profileId: urn:fdc:peppol.eu:2017:poacc:billing:01:1.0

invoice:
  id: "4711"
  issueDate: "2026-04-14"
  dueDate: "2026-05-14"
  typeCode: "380"
  documentCurrencyCode: EUR
  buyerReference: 04011000-12345-34
  accountingCost: KST-1000

projectReferenceId: PROJ-2026-01
contractReferenceId: RV-99
receiptReferenceId: WAR-100
despatchReferenceId: LFS-100
originatorReferenceId: DEMAND-77

orderReference:
  id: ORD-2026-12
  salesOrderId: SO-2026-12

precedingInvoices:
  - id: RE-2025-0007
    issueDate: "2025-12-31"
```

### Notizen

```yaml
notes:
  - subjectCode: AAK
    text: Zusatzhinweis fuer die Abrechnung
```

### Identifier mit `schemeID`

```yaml
seller:
  endpoint:
    value: rechnung@example.org
    schemeId: EM
```

### Partei

```yaml
seller:
  name: Lieferant GmbH
  tradeName: Lieferant Services
  endpoint:
    value: 9925:lieferant@example.org
    schemeId: EM
  identifiers:
    - value: 123456789
      schemeId: 0088
  vatIdentifier: DE123456789
  legalRegistrationId: HRB12345
  legalRegistrationIdSchemeId: 0204
  legalForm: GmbH
  address:
    street: Hauptstrasse 1
    additionalStreet: Haus B
    addressLine: 3. Etage
    city: Bonn
    postalCode: "53111"
    countrySubdivision: NW
    countryCode: DE
  contact:
    name: Rechnungsstelle
    phone: "+49-228-1234"
    email: rechnung@example.org
```

### Lieferung

```yaml
delivery:
  partyName: Nebenstelle Musterstadt
  actualDate: "2026-04-12"
  location:
    id: 1234567890123
    schemeId: 0088
  address:
    street: Lagerweg 4
    additionalStreet: Tor 2
    addressLine: Halle C
    city: Musterstadt
    postalCode: "12345"
    countryCode: DE
```

### Zahlung

```yaml
payment:
  meansCode: "58"
  meansText: SEPA credit transfer
  paymentId: RE-2026-4711
  payeeAccounts:
    - id: DE02120300000000202051
      name: Lieferant GmbH
      bic: BYLADEM1001

paymentTerms:
  note: Zahlbar innerhalb von 30 Tagen netto.
```

### Dokumentreferenzen und Anlagen

```yaml
supportingDocuments:
  - id: anhang-01
    description: Leistungsnachweis
    embedded:
      externalUri: https://example.org/nachweis/4711
  - id: ext-01
    description: Externer Nachweis
    embedded:
      externalUri: https://example.org/nachweis/4711
```

### Nachlass / Zuschlag / Steuer

```yaml
documentAllowances:
  - amount: 15.00
    baseAmount: 150.00
    percent: 10
    reason: Projektrabatt
    reasonCode: 95
    tax:
      categoryCode: S
      rate: 19

vatBreakdowns:
  - taxableAmount: 100.00
    taxAmount: 19.00
    categoryCode: S
    rate: 19

totals:
  lineExtensionAmount: 100.00
  taxExclusiveAmount: 100.00
  taxAmountInDocumentCurrency: 19.00
  taxInclusiveAmount: 119.00
  payableAmount: 119.00
```

### Rechnungsposition

```yaml
lines:
  - id: "1"
    note: Leistung April
    objectReference:
      id: ZAeHLER-44
      schemeId: AAB
    quantity: 2
    quantityUnitCode: HUR
    lineExtensionAmount: 240.00
    accountingCost: KST-1000
    orderLineReference: "10"
    period:
      startDate: "2026-04-01"
      endDate: "2026-04-02"
    allowances:
      - amount: 10.00
    item:
      name: Beratung
      description: Workshop und Dokumentation
      sellersItemId: B-1000
      standardId: 04012345123456
      standardIdSchemeId: 0160
      classifications:
        - code: 12345678
          listId: STI
          listVersionId: "23.1"
      originCountryCode: DE
      attributes:
        - name: Farbe
          value: Rot
    vat:
      categoryCode: S
      rate: 19
    price:
      netAmount: 120.00
      discount:
        amount: 10.00
        baseAmount: 130.00
      baseQuantity: 1
      baseQuantityUnitCode: HUR
```

## Sonderformen, die explizit bleiben

- `BT-8` ist semantisch top-level, rendert aber in dieselbe UBL-Struktur wie
  `BG-14`. Im Modell steckt das deshalb in
  `invoicePeriod.descriptionCode`.
- `BT-18` und `BT-128` verwenden keine freie Dokumentreferenz, sondern
  `DocumentTypeCode = 130`.
- `BT-82` ist in UBL kein eigenes Element, sondern `@name` an
  `cbc:PaymentMeansCode`.
- `BT-90` steht syntaktisch nicht in `PaymentMeans`, sondern als
  `PartyIdentification` mit `schemeID = "SEPA"` bei Seller oder Payee.
- `BT-111` ist in UBL ein weiterer `cac:TaxTotal/cbc:TaxAmount` mit
  `currencyID = BT-6`.

## Aenderungsregel

Bei Standardaenderungen gilt:

1. Zuerst die Mapping-Datei anpassen.
2. Pruefen, ob die neue Struktur bereits zu einem vorhandenen Pattern passt.
3. Nur wenn wirklich ein neues Strukturmuster auftaucht, ein neues Macro
   einfuehren.
4. Die Dokumentkomposition sollte moeglichst nur Makro-Aufrufe enthalten.

## Konsequenz

Je mehr `Invoice.vm` nur noch aus solchen Bausteinen besteht, desto eher bleibt
ein Versionsupdate ohne Java-Code-Aenderung:

- neue Felder im existierenden Block:
  meistens nur Macro oder Aufruf erweitern
- neue optionale Bloecke:
  neuen Macro-Aufruf einhaengen
- neue Wiederholungslogik:
  nur dann neues Macro, wenn kein bestehendes Pattern passt

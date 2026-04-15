# Public Model Contract fuer `$xr`

Oeffentliche Modellbeschreibung zwischen interner DTO-Aufbereitung und
tolerantem Velocity-Rendering fuer `UBL Invoice`.

Dateien:

- [ubl-invoice-full-stub.yaml](./ubl-invoice-full-stub.yaml)
- [ubl-invoice-core-stub.yaml](./ubl-invoice-core-stub.yaml)
- [ubl-invoice-core-mapping.yaml](./ubl-invoice-core-mapping.yaml)

## Ziel

`$xr` soll XRechnung-semantisch sein:

- nicht DTO-spezifisch
- nicht direkt UBL-spezifisch
- stabil fuer Template-Updates ohne Java-Code-Aenderung

## Grundregeln

- Fehlende Werte duerfen `null` sein oder ganz fehlen.
- Leere Strings sollten vor dem Rendern zu `null` normalisiert werden.
- Numerische `0` ist ein echter Wert und darf nicht als "fehlt" behandelt
  werden.
- Listen duerfen leer sein.
- Ein Listenobjekt mit nur `null`-Feldern rendert im toleranten Template
  effektiv zu nichts.

## Datentyp-Konventionen

| Kategorie | Erwartete Form |
|---|---|
| `text`, `code`, `identifier` | String |
| `date` | String im Format `YYYY-MM-DD` |
| `amount`, `quantity`, `percentage` | bereits normalisierter Decimal-Wert, bevorzugt kein `double`/`float` |
| Listen | YAML/JSON-Array |
| Komplexe Bloecke | Objekt/Map |

## Top-Level-Struktur

| Path | Form | Bedeutung |
|---|---|---|
| `xr.process` | object | `BT-23`, `BT-24` |
| `xr.invoice` | object | Kopfwerte wie `BT-1`, `BT-2`, `BT-3`, `BT-5`, `BT-6`, `BT-7`, `BT-9`, `BT-10`, `BT-19` |
| `xr.notes[]` | list<object> | `BG-1` |
| `xr.invoicePeriod` | object | `BT-8`, `BG-14` |
| `xr.projectReferenceId` | scalar | `BT-11` |
| `xr.contractReferenceId` | scalar | `BT-12` |
| `xr.receiptReferenceId` | scalar | `BT-15` |
| `xr.despatchReferenceId` | scalar | `BT-16` |
| `xr.originatorReferenceId` | scalar | `BT-17` |
| `xr.orderReference` | object | `BT-13`, `BT-14` |
| `xr.precedingInvoices[]` | list<object> | `BG-3` |
| `xr.invoiceObjectReference` | object | `BT-18` |
| `xr.seller` | object | `BG-4..6` |
| `xr.buyer` | object | `BG-7..9` |
| `xr.payee` | object | `BG-10` |
| `xr.taxRepresentative` | object | `BG-11..12` |
| `xr.delivery` | object | `BG-13`, `BG-15` |
| `xr.payment` | object | `BG-16..19` |
| `xr.paymentTerms` | object | `BT-20` |
| `xr.documentAllowances[]` | list<object> | `BG-20` |
| `xr.documentCharges[]` | list<object> | `BG-21` |
| `xr.totals` | object | `BG-22` |
| `xr.vatBreakdowns[]` | list<object> | `BG-23` |
| `xr.supportingDocuments[]` | list<object> | `BG-24` |
| `xr.lines[]` | list<object> | `BG-25..32` |

## Wiederverwendete Unterstrukturen

| Struktur | Felder |
|---|---|
| `identifier object` | `value`, optional `schemeId` |
| `address object` | `street`, `additionalStreet`, `addressLine`, `city`, `postalCode`, `countrySubdivision`, `countryCode` |
| `contact object` | `name`, `phone`, `email` |
| `tax object` | `categoryCode`, `rate`, optional `exemptionReason`, `exemptionReasonCode` |
| `allowance/charge object` | `amount`, `baseAmount`, `percent`, `reason`, `reasonCode`, optional `tax` |
| `attachment object` | optional `externalUri`, optional `content`, optional `mimeCode`, optional `filename` |
| `classification object` | `code`, `listId`, optional `listVersionId` |
| `item attribute object` | `name`, `value` |

## Modellform

- `BT-8` steckt in `xr.invoicePeriod.descriptionCode`, obwohl es semantisch
  top-level definiert ist, weil es in UBL in dieselbe `cac:InvoicePeriod`
  rendert.
- `BT-18` und `BT-128` haben eine eigene Objektform mit `id` und `schemeId`,
  weil UBL dafuer einen festen `DocumentTypeCode = 130` erwartet.
- `BT-82` heisst im oeffentlichen Modell `payment.meansText`, obwohl es
  syntaktisch als `@name` an `cbc:PaymentMeansCode` haengt.
- `BT-90` steht bei Seller oder Payee als `sepaCreditorId`, weil das dem
  UBL-Binding entspricht.
- `BT-29` bleibt als `seller.identifiers[]` wiederholbar; `BT-46` und `BT-60`
  bleiben dagegen singulaer als `buyer.identifier` und `payee.identifier`.
- `BT-46`, `BT-60`, `BG-18` und `BG-19` bleiben im oeffentlichen Modell
  singulaer.
- `BT-111` ist in `xr.totals.taxAmountInTaxCurrency` getrennt von
  `taxAmountInDocumentCurrency`, weil daraus in UBL zwei verschiedene
  `cac:TaxTotal/cbc:TaxAmount`-Ausgaben entstehen koennen.

## Vor Velocity

- Rundung und Skalierung von `amount`, `quantity`, `percentage`
- Bildung von `totals`
- Bildung von `vatBreakdowns`
- Normalisierung leerer Strings
- Auswahl, ob ein Feld als `null`, leer oder mit Wert in das Public Model geht

Das Template soll rendern, nicht rechnen.

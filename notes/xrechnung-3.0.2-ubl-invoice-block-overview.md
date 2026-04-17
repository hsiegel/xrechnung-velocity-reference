# XRechnung 3.0.2 Block Overview fuer `UBL Invoice`

## Grundlage

- Quelle:
  [README.md](../bundle-docs/xrechnung/bundle/README.md).
- Spezifikation: `XRechnung Specification 3.0.2`.
- Syntax-Binding: `zu 3.0.2`.
- Struktur:
  [xrechnung-cius-model.xml](../bundle-docs/xrechnung/model/semox/xrechnung-cius-model.xml).
- Diese Uebersicht beschreibt `UBL Invoice` (`ubl-inv`).

## Kopf und Referenzen

| Abschnitt | BT/BG | Empfohlener Model Path | UBL-Form | Macro/Pattern |
|---|---|---|---|---|
| Kopfwerte | `BT-1`, `BT-2`, `BT-3`, `BT-5`, `BT-6`, `BT-7`, `BT-9`, `BT-10`, `BT-19` | `invoice.*` | `cbc:*` | Primitive Macros |
| Notizen | `BG-1`, `BT-21`, `BT-22` | `notes[]` | `cbc:Note` | `xrNote` |
| Prozesskennung | `BG-2`, `BT-23`, `BT-24` | `process.*` | `cbc:ProfileID`, `cbc:CustomizationID` | Primitive Macros |
| Projektreferenz | `BT-11` | `projectReferenceId` | `cac:ProjectReference/cbc:ID` | `xrProjectReference` |
| Vertragsreferenz | `BT-12` | `contractReferenceId` | `cac:ContractDocumentReference/cbc:ID` | `xrDocumentReference` |
| Bestellreferenz | `BT-13`, `BT-14` | `orderReference.*` | `cac:OrderReference` | `xrOrderReference` |
| Wareneingangsreferenz | `BT-15` | `receiptReferenceId` | `cac:ReceiptDocumentReference/cbc:ID` | `xrDocumentReference` |
| Lieferavisreferenz | `BT-16` | `despatchReferenceId` | `cac:DespatchDocumentReference/cbc:ID` | `xrDocumentReference` |
| Anforderungsreferenz | `BT-17` | `originatorReferenceId` | `cac:OriginatorDocumentReference/cbc:ID` | `xrDocumentReference` |
| Rechnungsobjekt | `BT-18` | `invoiceObjectReference` | `cac:AdditionalDocumentReference` mit `DocumentTypeCode=130` | `xrInvoicedObjectReference` |
| Zahlungsbedingungen | `BT-20` | `paymentTerms.note` | `cac:PaymentTerms/cbc:Note` | `xrPaymentTerms` |
| Vorherige Rechnungen | `BG-3`, `BT-25`, `BT-26` | `precedingInvoices[]` | `cac:BillingReference/cac:InvoiceDocumentReference` | `xrBillingReference` |

## Parteien

| Block | BT/BG | Empfohlener Model Path | UBL-Form | Macro/Pattern |
|---|---|---|---|---|
| Seller | `BG-4`, `BT-27..34` | `seller` | `cac:AccountingSupplierParty/cac:Party` | `xrNestedParty` |
| Seller Address | `BG-5`, `BT-35..40`, `BT-162` | `seller.address` | `cac:PostalAddress` | `xrPostalAddress` |
| Seller Contact | `BG-6`, `BT-41..43` | `seller.contact` | `cac:Contact` | `xrContact` |
| Buyer | `BG-7`, `BT-44..49` | `buyer` | `cac:AccountingCustomerParty/cac:Party` | `xrNestedParty` |
| Buyer Address | `BG-8`, `BT-50..55`, `BT-163` | `buyer.address` | `cac:PostalAddress` | `xrPostalAddress` |
| Buyer Contact | `BG-9`, `BT-56..58` | `buyer.contact` | `cac:Contact` | `xrContact` |
| Payee | `BG-10`, `BT-59..61` | `payee` | `cac:PayeeParty` | `xrFlatParty` |
| Tax Representative | `BG-11`, `BT-62`, `BT-63` | `taxRepresentative` | `cac:TaxRepresentativeParty` | `xrFlatParty` |
| Tax Representative Address | `BG-12`, `BT-64..69`, `BT-164` | `taxRepresentative.address` | `cac:PostalAddress` | `xrPostalAddress` |

## Lieferung und Zahlung

| Block | BT/BG | Empfohlener Model Path | UBL-Form | Macro/Pattern |
|---|---|---|---|---|
| Delivery Information | `BG-13`, `BT-70..72` | `delivery` | `cac:Delivery` | `xrDelivery` |
| Deliver To Address | `BG-15`, `BT-75..80`, `BT-165` | `delivery.address` | `cac:Delivery/cac:DeliveryLocation/cac:Address` | `xrAddress` |
| Invoicing Period | `BG-14`, `BT-73`, `BT-74` | `invoicePeriod.startDate`, `invoicePeriod.endDate` | `cac:InvoicePeriod` | `xrInvoicePeriod` |
| VAT Point Date Code | `BT-8` | `invoicePeriod.descriptionCode` | `cac:InvoicePeriod/cbc:DescriptionCode` | `xrInvoicePeriod` |
| Payment Instructions | `BG-16`, `BT-81..83` | `payment` | `cac:PaymentMeans` | `xrPaymentMeans` |
| Credit Transfer | `BG-17`, `BT-84..86` | `payment.payeeAccounts[]` | `cac:PayeeFinancialAccount` | `xrPaymentMeans` |
| Payment Card | `BG-18`, `BT-87`, `BT-88` | `payment.card` | `cac:CardAccount` | `xrPaymentMeans` |
| Direct Debit | `BG-19`, `BT-89..91` | `payment.mandate` | `cac:PaymentMandate` | `xrPaymentMeans` |

## Summen, Steuer und Unterlagen

| Block | BT/BG | Empfohlener Model Path | UBL-Form | Macro/Pattern |
|---|---|---|---|---|
| Document Allowances | `BG-20`, `BT-92..98` | `documentAllowances[]` | `cac:AllowanceCharge` | `xrAllowanceCharge` |
| Document Charges | `BG-21`, `BT-99..105` | `documentCharges[]` | `cac:AllowanceCharge` | `xrAllowanceCharge` |
| Document Totals | `BG-22`, `BT-106..115` | `totals.*` | `cac:LegalMonetaryTotal` | `xrMonetaryTotal` |
| VAT Breakdown | `BG-23`, `BT-116..121` | `vatBreakdowns[]` | `cac:TaxTotal/cac:TaxSubtotal` | `xrTaxTotal` |
| VAT Total in Document Currency | `BT-110` | `totals.taxAmountInDocumentCurrency` | `cac:TaxTotal/cbc:TaxAmount[@currencyID=BT-5]` | `xrTaxTotal` |
| VAT Total in Tax Currency | `BT-111` | `totals.taxAmountInTaxCurrency` | `cac:TaxTotal/cbc:TaxAmount[@currencyID=BT-6]` | `xrTaxTotal` |
| Supporting Documents | `BG-24`, `BT-122..125` | `supportingDocuments[]` | `cac:AdditionalDocumentReference` | `xrAdditionalDocumentReference` |

## Positionen

| Block | BT/BG | Empfohlener Model Path | UBL-Form | Macro/Pattern |
|---|---|---|---|---|
| Invoice Line | `BG-25`, `BT-126`, `BT-127`, `BT-129..133` | `lines[]` | `cac:InvoiceLine` | `xrInvoiceLine` |
| Line Object Reference | `BT-128` | `line.objectReference` | `cac:InvoiceLine/cac:DocumentReference` mit `DocumentTypeCode=130` | `xrLineObjectReference` |
| Line Period | `BG-26`, `BT-134`, `BT-135` | `line.period` | `cac:InvoicePeriod` | `xrInvoicePeriod` |
| Line Allowances | `BG-27`, `BT-136..140` | `line.allowances[]` | `cac:AllowanceCharge` | `xrAllowanceCharge` |
| Line Charges | `BG-28`, `BT-141..145` | `line.charges[]` | `cac:AllowanceCharge` | `xrAllowanceCharge` |
| Price Details | `BG-29`, `BT-146..150` | `line.price` | `cac:Price` | `xrPrice` |
| Line VAT | `BG-30`, `BT-151`, `BT-152` | `line.vat` | `cac:ClassifiedTaxCategory` | `xrTaxCategory` |
| Item Information | `BG-31`, `BT-153..159` | `line.item` | `cac:Item` | `xrItem` |
| Item Attributes | `BG-32`, `BT-160`, `BT-161` | `line.item.attributes[]` | `cac:AdditionalItemProperty` | `xrItem` |

## Sonderfaelle im UBL-Binding

- `BT-8` steht semantisch oben, rendert aber in dieselbe `cac:InvoicePeriod`
  wie `BG-14`.
- `BT-18` und `BT-128` sind keine beliebigen Referenzen, sondern UBL-Container
  mit festem `cbc:DocumentTypeCode` `130`.
- `BT-82` ist `cbc:PaymentMeansCode/@name`, also kein eigenes XML-Element.
- `BT-90` ist ein `cac:PartyIdentification/cbc:ID` mit `@schemeID = "SEPA"`
  am Seller oder Payee, nicht im `PaymentMandate`.
- `BT-111` ist ein weiterer `cac:TaxTotal/cbc:TaxAmount` in der
  Abrechnungswaehrung aus `BT-6`.

## Template-Prinzip

- Das semantische Modell soll XRechnung-semantisch bleiben, nicht DTO-nah und
  nicht UBL-nah.
- Die Dokument-Schale sollte moeglichst nur aus Macro-Aufrufen bestehen.
- Sonderformen bleiben sichtbar, damit sie bei spaeteren Standardupdates nicht
  versteckt in Java-Code wandern.

{-# LANGUAGE OverloadedStrings #-}

module XRechnung.Ubl.Render
  ( toUblDocument
  , renderUblXml
  , renderUblXmlChecked
  ) where

import Data.Text (Text)
import Text.XML (Document, Element, Node)
import XRechnung.Model
import XRechnung.Ubl.Internal.Builder
import XRechnung.Validation (Validation)
import XRechnung.Verify (verify)

toUblDocument :: XRechnung -> Document
toUblDocument xr = document (invoiceElement xr)

renderUblXml :: XRechnung -> Text
renderUblXml = renderDocumentText . toUblDocument

renderUblXmlChecked :: XRechnung -> Either Validation Text
renderUblXmlChecked xr =
  case verify xr of
    [] -> Right (renderUblXml xr)
    violations -> Left violations

invoiceElement :: XRechnung -> Element
invoiceElement xr =
  element invoiceName [] $
    headerNodes xr
      ++ referenceNodes xr
      ++ partyNodes xr
      ++ deliveryAndPaymentNodes xr
      ++ documentAllowanceChargeNodes documentCurrency xr
      ++ taxAndTotalNodes documentCurrency taxCurrency xr
      ++ concatMap (invoiceLineNodes documentCurrency) (xrechnungLines xr)
  where
    documentCurrency = invoiceDocumentCurrencyCode =<< xrechnungInvoice xr
    taxCurrency = invoiceTaxCurrencyCode =<< xrechnungInvoice xr

headerNodes :: XRechnung -> [Node]
headerNodes xr =
  processNodes (xrechnungProcess xr)
    ++ invoiceLeadNodes (xrechnungInvoice xr)
    ++ concatMap noteNodes (xrechnungNotes xr)
    ++ invoiceTailNodes (xrechnungInvoice xr)
    ++ invoicePeriodNodes (xrechnungInvoicePeriod xr)

processNodes :: Maybe Process -> [Node]
processNodes Nothing = []
processNodes (Just process) =
  optTextNode (cbcName "CustomizationID") (processCustomizationId process)
    ++ optTextNode (cbcName "ProfileID") (processProfileId process)

invoiceLeadNodes :: Maybe Invoice -> [Node]
invoiceLeadNodes Nothing = []
invoiceLeadNodes (Just invoice) =
  optTextNode (cbcName "ID") (invoiceId invoice)
    ++ optDayNode (cbcName "IssueDate") (invoiceIssueDate invoice)
    ++ optDayNode (cbcName "DueDate") (invoiceDueDate invoice)
    ++ optTextNode (cbcName "InvoiceTypeCode") (invoiceTypeCode invoice)

noteNodes :: Note -> [Node]
noteNodes note =
  case noteText note of
    Nothing -> []
    Just body ->
      let rendered =
            maybe body (\subjectCode -> "#" <> subjectCode <> "#" <> body) (noteSubjectCode note)
       in [textElement (cbcName "Note") [] rendered]

invoiceTailNodes :: Maybe Invoice -> [Node]
invoiceTailNodes Nothing = []
invoiceTailNodes (Just invoice) =
  optDayNode (cbcName "TaxPointDate") (invoiceTaxPointDate invoice)
    ++ optTextNode (cbcName "DocumentCurrencyCode") (invoiceDocumentCurrencyCode invoice)
    ++ optTextNode (cbcName "TaxCurrencyCode") (invoiceTaxCurrencyCode invoice)
    ++ optTextNode (cbcName "AccountingCost") (invoiceAccountingCost invoice)
    ++ optTextNode (cbcName "BuyerReference") (invoiceBuyerReference invoice)

invoicePeriodNodes :: Maybe InvoicePeriod -> [Node]
invoicePeriodNodes Nothing = []
invoicePeriodNodes (Just period) =
  wrapIfAny (cacName "InvoicePeriod") [] $
    optDayNode (cbcName "StartDate") (invoicePeriodStartDate period)
      ++ optDayNode (cbcName "EndDate") (invoicePeriodEndDate period)
      ++ optTextNode (cbcName "DescriptionCode") (invoicePeriodDescriptionCode period)

referenceNodes :: XRechnung -> [Node]
referenceNodes xr =
  orderReferenceNodes (xrechnungOrderReference xr)
    ++ concatMap billingReferenceNodes (xrechnungPrecedingInvoices xr)
    ++ qualifiedDocumentReferenceNodes "DespatchDocumentReference" (xrechnungDespatchReferenceId xr)
    ++ qualifiedDocumentReferenceNodes "ReceiptDocumentReference" (xrechnungReceiptReferenceId xr)
    ++ qualifiedDocumentReferenceNodes "OriginatorDocumentReference" (xrechnungOriginatorReferenceId xr)
    ++ qualifiedDocumentReferenceNodes "ContractDocumentReference" (xrechnungContractReferenceId xr)
    ++ invoiceObjectReferenceNodes (xrechnungInvoiceObjectReference xr)
    ++ concatMap supportingDocumentNodes (xrechnungSupportingDocuments xr)
    ++ projectReferenceNodes (xrechnungProjectReferenceId xr)

projectReferenceNodes :: Maybe Text -> [Node]
projectReferenceNodes projectReferenceId =
  wrapIfAny (cacName "ProjectReference") [] $
    optTextNode (cbcName "ID") projectReferenceId

qualifiedDocumentReferenceNodes :: Text -> Maybe Text -> [Node]
qualifiedDocumentReferenceNodes wrapperTag referenceId =
  wrapIfAny (cacName wrapperTag) [] $
    optTextNode (cbcName "ID") referenceId

orderReferenceNodes :: Maybe OrderReference -> [Node]
orderReferenceNodes Nothing = []
orderReferenceNodes (Just reference) =
  wrapIfAny (cacName "OrderReference") [] $
    optTextNode (cbcName "ID") (orderReferenceId reference)
      ++ optTextNode (cbcName "SalesOrderID") (orderReferenceSalesOrderId reference)

billingReferenceNodes :: PrecedingInvoice -> [Node]
billingReferenceNodes reference =
  wrapIfAny (cacName "BillingReference") [] $
    wrapIfAny (cacName "InvoiceDocumentReference") [] $
      optTextNode (cbcName "ID") (precedingInvoiceId reference)
        ++ optDayNode (cbcName "IssueDate") (precedingInvoiceIssueDate reference)

invoiceObjectReferenceNodes :: Maybe Reference -> [Node]
invoiceObjectReferenceNodes Nothing = []
invoiceObjectReferenceNodes (Just reference) =
  wrapIfAny (cacName "AdditionalDocumentReference") [] $
    optIdentifierNode (cbcName "ID") (referenceId reference) (referenceSchemeId reference)
      ++ [textElement (cbcName "DocumentTypeCode") [] "130"]

supportingDocumentNodes :: SupportingDocument -> [Node]
supportingDocumentNodes reference =
  wrapIfAny (cacName "AdditionalDocumentReference") [] $
    optTextNode (cbcName "ID") (supportingDocumentId reference)
      ++ optTextNode (cbcName "DocumentDescription") (supportingDocumentDescription reference)
      ++ attachmentNodes (supportingDocumentEmbedded reference)

attachmentNodes :: Maybe Attachment -> [Node]
attachmentNodes Nothing = []
attachmentNodes (Just attachment) =
  wrapIfAny (cacName "Attachment") [] $
    embeddedAttachmentNodes attachment
      ++ externalAttachmentNodes attachment

embeddedAttachmentNodes :: Attachment -> [Node]
embeddedAttachmentNodes attachment =
  case attachmentContent attachment of
    Nothing -> []
    Just content ->
      [ textElement
          (cbcName "EmbeddedDocumentBinaryObject")
          ( attrMaybe "mimeCode" (attachmentMimeCode attachment)
              ++ attrMaybe "filename" (attachmentFilename attachment)
          )
          content
      ]

externalAttachmentNodes :: Attachment -> [Node]
externalAttachmentNodes attachment =
  wrapIfAny (cacName "ExternalReference") [] $
    optTextNode (cbcName "URI") (attachmentExternalUri attachment)

partyNodes :: XRechnung -> [Node]
partyNodes xr =
  accountingSupplierPartyNodes (xrechnungSeller xr)
    ++ accountingCustomerPartyNodes (xrechnungBuyer xr)
    ++ payeePartyNodes (xrechnungPayee xr)
    ++ taxRepresentativePartyNodes (xrechnungTaxRepresentative xr)

accountingSupplierPartyNodes :: Maybe Seller -> [Node]
accountingSupplierPartyNodes Nothing = []
accountingSupplierPartyNodes (Just seller) =
  wrapNestedParty "AccountingSupplierParty" $
    endpointNodes (sellerEndpoint seller)
      ++ sepaCreditorIdNodes (sellerSepaCreditorId seller)
      ++ concatMap partyIdentifierNodes (sellerIdentifiers seller)
      ++ partyNameNodes (sellerTradeName seller)
      ++ postalAddressNodes (sellerAddress seller)
      ++ partyTaxSchemeNodes "VAT" (sellerVatIdentifier seller)
      ++ partyTaxSchemeNodes "TAX" (sellerTaxIdentifier seller)
      ++ partyLegalEntityNodes
        (sellerName seller)
        (sellerLegalRegistrationId seller)
        (sellerLegalRegistrationIdSchemeId seller)
        (sellerLegalForm seller)
      ++ contactNodes (sellerContact seller)

accountingCustomerPartyNodes :: Maybe Buyer -> [Node]
accountingCustomerPartyNodes Nothing = []
accountingCustomerPartyNodes (Just buyer) =
  wrapNestedParty "AccountingCustomerParty" $
    endpointNodes (buyerEndpoint buyer)
      ++ partyIdentifierNodesMaybe (buyerIdentifier buyer)
      ++ partyNameNodes (buyerTradeName buyer)
      ++ postalAddressNodes (buyerAddress buyer)
      ++ partyTaxSchemeNodes "VAT" (buyerVatIdentifier buyer)
      ++ partyLegalEntityNodes
        (buyerName buyer)
        (buyerLegalRegistrationId buyer)
        (buyerLegalRegistrationIdSchemeId buyer)
        Nothing
      ++ contactNodes (buyerContact buyer)

payeePartyNodes :: Maybe Payee -> [Node]
payeePartyNodes Nothing = []
payeePartyNodes (Just payee) =
  wrapIfAny (cacName "PayeeParty") [] $
    sepaCreditorIdNodes (payeeSepaCreditorId payee)
      ++ partyIdentifierNodesMaybe (payeeIdentifier payee)
      ++ partyNameNodes (payeeName payee)
      ++ partyLegalEntityNodes
        Nothing
        (payeeLegalRegistrationId payee)
        (payeeLegalRegistrationIdSchemeId payee)
        Nothing

taxRepresentativePartyNodes :: Maybe TaxRepresentative -> [Node]
taxRepresentativePartyNodes Nothing = []
taxRepresentativePartyNodes (Just representative) =
  wrapIfAny (cacName "TaxRepresentativeParty") [] $
    partyNameNodes (taxRepresentativeName representative)
      ++ postalAddressNodes (taxRepresentativeAddress representative)
      ++ partyTaxSchemeNodes "VAT" (taxRepresentativeVatIdentifier representative)

wrapNestedParty :: Text -> [Node] -> [Node]
wrapNestedParty wrapperTag partyChildren =
  wrapIfAny (cacName wrapperTag) [] $
    wrapIfAny (cacName "Party") [] partyChildren

endpointNodes :: Maybe Identifier -> [Node]
endpointNodes Nothing = []
endpointNodes (Just identifier) =
  optIdentifierNode (cbcName "EndpointID") (identifierValue identifier) (identifierSchemeId identifier)

partyIdentifierNodesMaybe :: Maybe Identifier -> [Node]
partyIdentifierNodesMaybe Nothing = []
partyIdentifierNodesMaybe (Just identifier) = partyIdentifierNodes identifier

partyIdentifierNodes :: Identifier -> [Node]
partyIdentifierNodes identifier =
  wrapIfAny (cacName "PartyIdentification") [] $
    optIdentifierNode (cbcName "ID") (identifierValue identifier) (identifierSchemeId identifier)

sepaCreditorIdNodes :: Maybe Text -> [Node]
sepaCreditorIdNodes creditorId =
  wrapIfAny (cacName "PartyIdentification") [] $
    optIdentifierNode (cbcName "ID") creditorId (Just "SEPA")

partyNameNodes :: Maybe Text -> [Node]
partyNameNodes name =
  wrapIfAny (cacName "PartyName") [] $
    optTextNode (cbcName "Name") name

postalAddressNodes :: Maybe Address -> [Node]
postalAddressNodes = addressNodes "PostalAddress"

addressNodes :: Text -> Maybe Address -> [Node]
addressNodes _ Nothing = []
addressNodes wrapperTag (Just address) =
  wrapIfAny (cacName wrapperTag) [] $
    optTextNode (cbcName "StreetName") (addressStreet address)
      ++ optTextNode (cbcName "AdditionalStreetName") (addressAdditionalStreet address)
      ++ optTextNode (cbcName "CityName") (addressCity address)
      ++ optTextNode (cbcName "PostalZone") (addressPostalCode address)
      ++ optTextNode (cbcName "CountrySubentity") (addressCountrySubdivision address)
      ++ addressLineNodes (addressAddressLine address)
      ++ countryNodes (addressCountryCode address)

addressLineNodes :: Maybe Text -> [Node]
addressLineNodes addressLine =
  wrapIfAny (cacName "AddressLine") [] $
    optTextNode (cbcName "Line") addressLine

countryNodes :: Maybe Text -> [Node]
countryNodes countryCode =
  wrapIfAny (cacName "Country") [] $
    optTextNode (cbcName "IdentificationCode") countryCode

contactNodes :: Maybe Contact -> [Node]
contactNodes Nothing = []
contactNodes (Just contact) =
  wrapIfAny (cacName "Contact") [] $
    optTextNode (cbcName "Name") (contactName contact)
      ++ optTextNode (cbcName "Telephone") (contactPhone contact)
      ++ optTextNode (cbcName "ElectronicMail") (contactEmail contact)

partyTaxSchemeNodes :: Text -> Maybe Text -> [Node]
partyTaxSchemeNodes schemeId companyId =
  case companyId of
    Nothing -> []
    Just companyIdentifier ->
      wrapIfAny (cacName "PartyTaxScheme") [] $
        optTextNode (cbcName "CompanyID") (Just companyIdentifier)
          ++ wrapIfAny (cacName "TaxScheme") [] [textElement (cbcName "ID") [] schemeId]

partyLegalEntityNodes :: Maybe Text -> Maybe Text -> Maybe Text -> Maybe Text -> [Node]
partyLegalEntityNodes registrationName companyId companyIdSchemeId legalForm =
  wrapIfAny (cacName "PartyLegalEntity") [] $
    optTextNode (cbcName "RegistrationName") registrationName
      ++ optIdentifierNode (cbcName "CompanyID") companyId companyIdSchemeId
      ++ optTextNode (cbcName "CompanyLegalForm") legalForm

deliveryAndPaymentNodes :: XRechnung -> [Node]
deliveryAndPaymentNodes xr =
  deliveryNodes (xrechnungDelivery xr)
    ++ paymentMeansNodes (xrechnungPayment xr)
    ++ paymentTermsNodes (xrechnungPaymentTerms xr)

deliveryNodes :: Maybe Delivery -> [Node]
deliveryNodes Nothing = []
deliveryNodes (Just delivery) =
  wrapIfAny (cacName "Delivery") [] $
    optDayNode (cbcName "ActualDeliveryDate") (deliveryActualDate delivery)
      ++ deliveryLocationNodes delivery
      ++ deliveryPartyNodes delivery

deliveryLocationNodes :: Delivery -> [Node]
deliveryLocationNodes delivery =
  wrapIfAny (cacName "DeliveryLocation") [] $
    optIdentifierNode
      (cbcName "ID")
      (referenceId =<< deliveryLocation delivery)
      (referenceSchemeId =<< deliveryLocation delivery)
      ++ addressNodes "Address" (deliveryAddress delivery)

deliveryPartyNodes :: Delivery -> [Node]
deliveryPartyNodes delivery =
  wrapIfAny (cacName "DeliveryParty") [] $
    partyNameNodes (deliveryPartyName delivery)

paymentMeansNodes :: Maybe Payment -> [Node]
paymentMeansNodes Nothing = []
paymentMeansNodes (Just payment) =
  wrapIfAny (cacName "PaymentMeans") [] $
    paymentMeansCodeNodes payment
      ++ optTextNode (cbcName "PaymentID") (paymentPaymentId payment)
      ++ cardAccountNodes (paymentCard payment)
      ++ concatMap payeeFinancialAccountNodes (paymentPayeeAccounts payment)
      ++ paymentMandateNodes (paymentMandate payment)

paymentMeansCodeNodes :: Payment -> [Node]
paymentMeansCodeNodes payment =
  case paymentMeansCode payment of
    Nothing -> []
    Just meansCode ->
      [ textElement
          (cbcName "PaymentMeansCode")
          (attrMaybe "name" (paymentMeansText payment))
          meansCode
      ]

cardAccountNodes :: Maybe PaymentCard -> [Node]
cardAccountNodes Nothing = []
cardAccountNodes (Just card) =
  wrapIfAny (cacName "CardAccount") [] $
    optTextNode (cbcName "PrimaryAccountNumberID") (paymentCardPrimaryAccountNumberId card)
      ++ optTextNode (cbcName "HolderName") (paymentCardHolderName card)

payeeFinancialAccountNodes :: PayeeAccount -> [Node]
payeeFinancialAccountNodes account =
  wrapIfAny (cacName "PayeeFinancialAccount") [] $
    optTextNode (cbcName "ID") (payeeAccountId account)
      ++ optTextNode (cbcName "Name") (payeeAccountName account)
      ++ financialInstitutionBranchNodes (payeeAccountBic account)

financialInstitutionBranchNodes :: Maybe Text -> [Node]
financialInstitutionBranchNodes bic =
  wrapIfAny (cacName "FinancialInstitutionBranch") [] $
    optTextNode (cbcName "ID") bic

paymentMandateNodes :: Maybe PaymentMandate -> [Node]
paymentMandateNodes Nothing = []
paymentMandateNodes (Just mandate) =
  wrapIfAny (cacName "PaymentMandate") [] $
    optTextNode (cbcName "ID") (paymentMandateId mandate)
      ++ payerFinancialAccountNodes (paymentMandatePayerAccountId mandate)

payerFinancialAccountNodes :: Maybe Text -> [Node]
payerFinancialAccountNodes payerAccountId =
  wrapIfAny (cacName "PayerFinancialAccount") [] $
    optTextNode (cbcName "ID") payerAccountId

paymentTermsNodes :: Maybe PaymentTerms -> [Node]
paymentTermsNodes Nothing = []
paymentTermsNodes (Just terms) =
  wrapIfAny (cacName "PaymentTerms") [] $
    optTextNode (cbcName "Note") (paymentTermsNote terms)

documentAllowanceChargeNodes :: Maybe Text -> XRechnung -> [Node]
documentAllowanceChargeNodes currency xr =
  concatMap (allowanceChargeNodes False currency) (xrechnungDocumentAllowances xr)
    ++ concatMap (allowanceChargeNodes True currency) (xrechnungDocumentCharges xr)

allowanceChargeNodes :: Bool -> Maybe Text -> DocumentAllowanceCharge -> [Node]
allowanceChargeNodes chargeIndicator currency entry =
  wrapIfAny (cacName "AllowanceCharge") [] $
    [textElement (cbcName "ChargeIndicator") [] (if chargeIndicator then "true" else "false")]
      ++ optTextNode (cbcName "AllowanceChargeReasonCode") (documentAllowanceChargeReasonCode entry)
      ++ optTextNode (cbcName "AllowanceChargeReason") (documentAllowanceChargeReason entry)
      ++ optDecimalNode (cbcName "MultiplierFactorNumeric") (documentAllowanceChargePercent entry)
      ++ optAmountNode (cbcName "Amount") (documentAllowanceChargeAmount entry) currency
      ++ optAmountNode (cbcName "BaseAmount") (documentAllowanceChargeBaseAmount entry) currency
      ++ taxCategoryNodes "TaxCategory"
        (documentAllowanceChargeTax entry >>= taxCategoryCode)
        (documentAllowanceChargeTax entry >>= taxRate)
        (documentAllowanceChargeTax entry >>= taxExemptionReason)
        (documentAllowanceChargeTax entry >>= taxExemptionReasonCode)

taxAndTotalNodes :: Maybe Text -> Maybe Text -> XRechnung -> [Node]
taxAndTotalNodes documentCurrency taxCurrency xr =
  taxTotalNodes documentCurrency taxCurrency (xrechnungTotals xr) (xrechnungVatBreakdowns xr)
    ++ monetaryTotalNodes documentCurrency (xrechnungTotals xr)

taxTotalNodes :: Maybe Text -> Maybe Text -> Maybe Totals -> [VatBreakdown] -> [Node]
taxTotalNodes documentCurrency taxCurrency maybeTotals vatBreakdowns =
  documentTaxTotalNodes documentCurrency maybeTotals vatBreakdowns
    ++ accountingTaxTotalNodes documentCurrency taxCurrency maybeTotals

documentTaxTotalNodes :: Maybe Text -> Maybe Totals -> [VatBreakdown] -> [Node]
documentTaxTotalNodes documentCurrency maybeTotals vatBreakdowns =
  wrapIfAny (cacName "TaxTotal") [] $
    optAmountNode
      (cbcName "TaxAmount")
      (maybeTotals >>= totalsTaxAmountInDocumentCurrency)
      documentCurrency
      ++ concatMap (taxSubtotalNodes documentCurrency) vatBreakdowns

accountingTaxTotalNodes :: Maybe Text -> Maybe Text -> Maybe Totals -> [Node]
accountingTaxTotalNodes documentCurrency taxCurrency maybeTotals =
  case maybeTotals >>= totalsTaxAmountInTaxCurrency of
    Nothing -> []
    Just accountingTaxAmount ->
      if shouldRenderAccountingTaxTotal documentCurrency taxCurrency
        then
          wrapIfAny (cacName "TaxTotal") [] $
            optAmountNode (cbcName "TaxAmount") (Just accountingTaxAmount) taxCurrency
        else []

shouldRenderAccountingTaxTotal :: Maybe Text -> Maybe Text -> Bool
shouldRenderAccountingTaxTotal documentCurrency taxCurrency =
  case taxCurrency of
    Nothing -> False
    Just accountingCurrency ->
      case documentCurrency of
        Nothing -> True
        Just documentCurrencyCode -> accountingCurrency /= documentCurrencyCode

taxSubtotalNodes :: Maybe Text -> VatBreakdown -> [Node]
taxSubtotalNodes currency breakdown =
  wrapIfAny (cacName "TaxSubtotal") [] $
    optAmountNode (cbcName "TaxableAmount") (vatBreakdownTaxableAmount breakdown) currency
      ++ optAmountNode (cbcName "TaxAmount") (vatBreakdownTaxAmount breakdown) currency
      ++ taxCategoryNodes
        "TaxCategory"
        (vatBreakdownCategoryCode breakdown)
        (vatBreakdownRate breakdown)
        (vatBreakdownExemptionReason breakdown)
        (vatBreakdownExemptionReasonCode breakdown)

taxCategoryNodes :: Text -> Maybe Text -> Maybe Decimal -> Maybe Text -> Maybe Text -> [Node]
taxCategoryNodes wrapperTag categoryCode rate exemptionReason exemptionReasonCode =
  if all isMissing [categoryCode, exemptionReason, exemptionReasonCode] && rate == Nothing
    then []
    else
      wrapIfAny (cacName wrapperTag) [] $
        optTextNode (cbcName "ID") categoryCode
          ++ optDecimalNode (cbcName "Percent") rate
          ++ optTextNode (cbcName "TaxExemptionReason") exemptionReason
          ++ optTextNode (cbcName "TaxExemptionReasonCode") exemptionReasonCode
          ++ wrapIfAny (cacName "TaxScheme") [] [textElement (cbcName "ID") [] "VAT"]

monetaryTotalNodes :: Maybe Text -> Maybe Totals -> [Node]
monetaryTotalNodes _ Nothing = []
monetaryTotalNodes currency (Just totals) =
  wrapIfAny (cacName "LegalMonetaryTotal") [] $
    optAmountNode (cbcName "LineExtensionAmount") (totalsLineExtensionAmount totals) currency
      ++ optAmountNode (cbcName "TaxExclusiveAmount") (totalsTaxExclusiveAmount totals) currency
      ++ optAmountNode (cbcName "TaxInclusiveAmount") (totalsTaxInclusiveAmount totals) currency
      ++ optAmountNode (cbcName "AllowanceTotalAmount") (totalsAllowanceTotalAmount totals) currency
      ++ optAmountNode (cbcName "ChargeTotalAmount") (totalsChargeTotalAmount totals) currency
      ++ optAmountNode (cbcName "PrepaidAmount") (totalsPrepaidAmount totals) currency
      ++ optAmountNode (cbcName "PayableRoundingAmount") (totalsPayableRoundingAmount totals) currency
      ++ optAmountNode (cbcName "PayableAmount") (totalsPayableAmount totals) currency

invoiceLineNodes :: Maybe Text -> Line -> [Node]
invoiceLineNodes currency line =
  wrapIfAny (cacName "InvoiceLine") [] $
    optTextNode (cbcName "ID") (lineId line)
      ++ optTextNode (cbcName "Note") (lineNote line)
      ++ optQuantityNode (cbcName "InvoicedQuantity") (lineQuantity line) (lineQuantityUnitCode line)
      ++ optAmountNode (cbcName "LineExtensionAmount") (lineLineExtensionAmount line) currency
      ++ optTextNode (cbcName "AccountingCost") (lineAccountingCost line)
      ++ linePeriodNodes (linePeriod line)
      ++ orderLineReferenceNodes (lineOrderLineReference line)
      ++ lineObjectReferenceNodes (lineObjectReference line)
      ++ concatMap (allowanceChargeLineNodes False currency) (lineAllowances line)
      ++ concatMap (allowanceChargeLineNodes True currency) (lineCharges line)
      ++ itemNodes (lineItem line) (lineVat line)
      ++ priceNodes (linePrice line) currency

linePeriodNodes :: Maybe LinePeriod -> [Node]
linePeriodNodes Nothing = []
linePeriodNodes (Just period) =
  wrapIfAny (cacName "InvoicePeriod") [] $
    optDayNode (cbcName "StartDate") (linePeriodStartDate period)
      ++ optDayNode (cbcName "EndDate") (linePeriodEndDate period)

orderLineReferenceNodes :: Maybe Text -> [Node]
orderLineReferenceNodes lineReference =
  wrapIfAny (cacName "OrderLineReference") [] $
    optTextNode (cbcName "LineID") lineReference

lineObjectReferenceNodes :: Maybe Reference -> [Node]
lineObjectReferenceNodes Nothing = []
lineObjectReferenceNodes (Just reference) =
  wrapIfAny (cacName "DocumentReference") [] $
    optIdentifierNode (cbcName "ID") (referenceId reference) (referenceSchemeId reference)
      ++ [textElement (cbcName "DocumentTypeCode") [] "130"]

allowanceChargeLineNodes :: Bool -> Maybe Text -> LineAllowanceCharge -> [Node]
allowanceChargeLineNodes chargeIndicator currency entry =
  wrapIfAny (cacName "AllowanceCharge") [] $
    [textElement (cbcName "ChargeIndicator") [] (if chargeIndicator then "true" else "false")]
      ++ optTextNode (cbcName "AllowanceChargeReasonCode") (lineAllowanceChargeReasonCode entry)
      ++ optTextNode (cbcName "AllowanceChargeReason") (lineAllowanceChargeReason entry)
      ++ optDecimalNode (cbcName "MultiplierFactorNumeric") (lineAllowanceChargePercent entry)
      ++ optAmountNode (cbcName "Amount") (lineAllowanceChargeAmount entry) currency
      ++ optAmountNode (cbcName "BaseAmount") (lineAllowanceChargeBaseAmount entry) currency

priceNodes :: Maybe LinePrice -> Maybe Text -> [Node]
priceNodes Nothing _ = []
priceNodes (Just price) currency =
  wrapIfAny (cacName "Price") [] $
    optAmountNode (cbcName "PriceAmount") (linePriceNetAmount price) currency
      ++ optQuantityNode (cbcName "BaseQuantity") (linePriceBaseQuantity price) (linePriceBaseQuantityUnitCode price)
      ++ priceDiscountNodes currency (linePriceDiscount price)

priceDiscountNodes :: Maybe Text -> Maybe PriceDiscount -> [Node]
priceDiscountNodes _ Nothing = []
priceDiscountNodes currency (Just discount) =
  wrapIfAny (cacName "AllowanceCharge") [] $
    [textElement (cbcName "ChargeIndicator") [] "false"]
      ++ optAmountNode (cbcName "Amount") (priceDiscountAmount discount) currency
      ++ optAmountNode (cbcName "BaseAmount") (priceDiscountBaseAmount discount) currency

itemNodes :: Maybe LineItem -> Maybe LineVat -> [Node]
itemNodes maybeItem maybeVat =
  case maybeItem of
    Nothing ->
      case maybeVat of
        Nothing -> []
        Just vat ->
          wrapIfAny (cacName "Item") [] $
            taxCategoryNodes
              "ClassifiedTaxCategory"
              (lineVatCategoryCode vat)
              (lineVatRate vat)
              Nothing
              Nothing
    Just item ->
      wrapIfAny (cacName "Item") [] $
        optTextNode (cbcName "Description") (lineItemDescription item)
          ++ optTextNode (cbcName "Name") (lineItemName item)
          ++ buyersItemIdentificationNodes (lineItemBuyersItemId item)
          ++ sellersItemIdentificationNodes (lineItemSellersItemId item)
          ++ standardItemIdentificationNodes
            (lineItemStandardId item)
            (lineItemStandardIdSchemeId item)
          ++ originCountryNodes (lineItemOriginCountryCode item)
          ++ concatMap commodityClassificationNodes (lineItemClassifications item)
          ++ maybe [] classifiedTaxCategoryNodes maybeVat
          ++ concatMap additionalItemPropertyNodes (lineItemAttributes item)

buyersItemIdentificationNodes :: Maybe Text -> [Node]
buyersItemIdentificationNodes buyersItemId =
  wrapIfAny (cacName "BuyersItemIdentification") [] $
    optTextNode (cbcName "ID") buyersItemId

sellersItemIdentificationNodes :: Maybe Text -> [Node]
sellersItemIdentificationNodes sellersItemId =
  wrapIfAny (cacName "SellersItemIdentification") [] $
    optTextNode (cbcName "ID") sellersItemId

standardItemIdentificationNodes :: Maybe Text -> Maybe Text -> [Node]
standardItemIdentificationNodes standardId standardIdSchemeId =
  wrapIfAny (cacName "StandardItemIdentification") [] $
    optIdentifierNode (cbcName "ID") standardId standardIdSchemeId

originCountryNodes :: Maybe Text -> [Node]
originCountryNodes originCountryCode =
  wrapIfAny (cacName "OriginCountry") [] $
    optTextNode (cbcName "IdentificationCode") originCountryCode

commodityClassificationNodes :: Classification -> [Node]
commodityClassificationNodes classification =
  wrapIfAny (cacName "CommodityClassification") [] $
    case classificationCode classification of
      Nothing -> []
      Just code ->
        [ textElement
            (cbcName "ItemClassificationCode")
            ( attrMaybe "listID" (classificationListId classification)
                ++ attrMaybe "listVersionID" (classificationListVersionId classification)
            )
            code
        ]

classifiedTaxCategoryNodes :: LineVat -> [Node]
classifiedTaxCategoryNodes vat =
  taxCategoryNodes
    "ClassifiedTaxCategory"
    (lineVatCategoryCode vat)
    (lineVatRate vat)
    Nothing
    Nothing

additionalItemPropertyNodes :: ItemAttribute -> [Node]
additionalItemPropertyNodes attribute =
  wrapIfAny (cacName "AdditionalItemProperty") [] $
    optTextNode (cbcName "Name") (itemAttributeName attribute)
      ++ optTextNode (cbcName "Value") (itemAttributeValue attribute)

isMissing :: Maybe a -> Bool
isMissing Nothing = True
isMissing _ = False

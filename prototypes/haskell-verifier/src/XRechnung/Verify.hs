{-# LANGUAGE AllowAmbiguousTypes #-}
{-# LANGUAGE DataKinds #-}
{-# LANGUAGE FlexibleContexts #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
{-# LANGUAGE TypeApplications #-}

module XRechnung.Verify (verify, verifyText) where

import Data.Char (toLower)
import Data.Generics.Product.Fields (HasField', getField)
import Data.List (foldl')
import Data.Maybe (fromMaybe, isJust, mapMaybe)
import Data.Ratio ((%))
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Set as Set
import XRechnung.Model
import XRechnung.Validation (Validation, ValidationIssue (..), renderIssues)

-- References used while implementing these checks:
-- - bundle-docs/xrechnung/model/semox/xrechnung-cius-model.xml
-- - bundle-docs/xrechnung/testsuite/test-overview.md
-- - semantic-model/xrechnung-3.0.2-pflichtfelder-overview.md

verify :: XRechnung -> Validation
verify xr =
  concat
    [ verifyMandatory xr
    , verifyOptionalGroups xr
    , verifyConsistency xr
    , verifyPayment xr
    , verifySupportingDocuments xr
    , verifyVatRules xr
    , verifyTotals xr
    , verifyLines xr
    ]

verifyText :: XRechnung -> [Text]
verifyText = renderIssues . verify

data CategoryInput = CategoryInput
  { categoryInputPath :: Text
  , categoryInputCode :: Text
  , categoryInputRate :: Maybe Decimal
  }

data BreakdownInfo = BreakdownInfo
  { breakdownPath :: Text
  , breakdownCode :: Text
  , breakdownTaxableAmount :: Maybe Decimal
  , breakdownTaxAmount :: Maybe Decimal
  , breakdownRate :: Maybe Decimal
  , breakdownHasReason :: Bool
  }

verifyMandatory :: XRechnung -> Validation
verifyMandatory xr =
  concat
    [ require "BT-24" "xr.process.customizationId" $
        presentNested @"xrechnungProcess" @"processCustomizationId" xr
    , require "BT-23" "xr.process.profileId" $
        presentNested @"xrechnungProcess" @"processProfileId" xr
    , require "BT-1" "xr.invoice.id" $
        presentNested @"xrechnungInvoice" @"invoiceId" xr
    , require "BT-2" "xr.invoice.issueDate" $
        existsNested @"xrechnungInvoice" @"invoiceIssueDate" xr
    , require "BT-3" "xr.invoice.typeCode" $
        presentNested @"xrechnungInvoice" @"invoiceTypeCode" xr
    , require "BT-5" "xr.invoice.documentCurrencyCode" $
        presentNested @"xrechnungInvoice" @"invoiceDocumentCurrencyCode" xr
    , require "BT-10" "xr.invoice.buyerReference" $
        presentNested @"xrechnungInvoice" @"invoiceBuyerReference" xr
    , require "BT-27" "xr.seller.name" $
        presentNested @"xrechnungSeller" @"sellerName" xr
    , require "BT-34" "xr.seller.endpoint.value" $
        presentNested2 @"xrechnungSeller" @"sellerEndpoint" @"identifierValue" xr
    , require "BT-34 / BR-62" "xr.seller.endpoint.schemeId" $
        presentNested2 @"xrechnungSeller" @"sellerEndpoint" @"identifierSchemeId" xr
    , require "BT-37" "xr.seller.address.city" $
        presentNested2 @"xrechnungSeller" @"sellerAddress" @"addressCity" xr
    , require "BT-38" "xr.seller.address.postalCode" $
        presentNested2 @"xrechnungSeller" @"sellerAddress" @"addressPostalCode" xr
    , require "BT-40" "xr.seller.address.countryCode" $
        presentNested2 @"xrechnungSeller" @"sellerAddress" @"addressCountryCode" xr
    , require "BT-41" "xr.seller.contact.name" $
        presentNested2 @"xrechnungSeller" @"sellerContact" @"contactName" xr
    , require "BT-42" "xr.seller.contact.phone" $
        presentNested2 @"xrechnungSeller" @"sellerContact" @"contactPhone" xr
    , require "BT-43" "xr.seller.contact.email" $
        presentNested2 @"xrechnungSeller" @"sellerContact" @"contactEmail" xr
    , require "BT-44" "xr.buyer.name" $
        presentNested @"xrechnungBuyer" @"buyerName" xr
    , require "BT-49" "xr.buyer.endpoint.value" $
        presentNested2 @"xrechnungBuyer" @"buyerEndpoint" @"identifierValue" xr
    , require "BT-49 / BR-63" "xr.buyer.endpoint.schemeId" $
        presentNested2 @"xrechnungBuyer" @"buyerEndpoint" @"identifierSchemeId" xr
    , require "BT-52" "xr.buyer.address.city" $
        presentNested2 @"xrechnungBuyer" @"buyerAddress" @"addressCity" xr
    , require "BT-53" "xr.buyer.address.postalCode" $
        presentNested2 @"xrechnungBuyer" @"buyerAddress" @"addressPostalCode" xr
    , require "BT-55" "xr.buyer.address.countryCode" $
        presentNested2 @"xrechnungBuyer" @"buyerAddress" @"addressCountryCode" xr
    , require "BT-81 / BR-DE-1" "xr.payment.meansCode" $
        presentNested @"xrechnungPayment" @"paymentMeansCode" xr
    , require "BT-106" "xr.totals.lineExtensionAmount" $
        existsNested @"xrechnungTotals" @"totalsLineExtensionAmount" xr
    , require "BT-109" "xr.totals.taxExclusiveAmount" $
        existsNested @"xrechnungTotals" @"totalsTaxExclusiveAmount" xr
    , require "BT-110 / BR-CO-14" "xr.totals.taxAmountInDocumentCurrency" $
        existsNested @"xrechnungTotals" @"totalsTaxAmountInDocumentCurrency" xr
    , require "BT-112" "xr.totals.taxInclusiveAmount" $
        existsNested @"xrechnungTotals" @"totalsTaxInclusiveAmount" xr
    , require "BT-115" "xr.totals.payableAmount" $
        existsNested @"xrechnungTotals" @"totalsPayableAmount" xr
    , requireNonEmpty "BR-CO-18" "xr.vatBreakdowns" (fieldValue @"xrechnungVatBreakdowns" xr)
    , requireNonEmpty "BG-25" "xr.lines" (fieldValue @"xrechnungLines" xr)
    , concatMap verifyMandatoryVatBreakdown (zip [1 :: Int ..] (fieldValue @"xrechnungVatBreakdowns" xr))
    , concatMap verifyMandatoryLine (zip [1 :: Int ..] (fieldValue @"xrechnungLines" xr))
    ]

verifyOptionalGroups :: XRechnung -> Validation
verifyOptionalGroups xr =
  concat
    [ concatMap verifyNote (zip [1 :: Int ..] (xrechnungNotes xr))
    , concatMap verifyPrecedingInvoice (zip [1 :: Int ..] (xrechnungPrecedingInvoices xr))
    , verifyPayee (xrechnungPayee xr)
    , verifyTaxRepresentative (xrechnungTaxRepresentative xr)
    , verifyDeliveryAddress (xrechnungDelivery xr)
    , concatMap verifyDocumentAllowance (zip [1 :: Int ..] (xrechnungDocumentAllowances xr))
    , concatMap verifyDocumentCharge (zip [1 :: Int ..] (xrechnungDocumentCharges xr))
    , concatMap verifySupportingDocumentOptional (zip [1 :: Int ..] (xrechnungSupportingDocuments xr))
    , concatMap verifyOptionalLineBits (zip [1 :: Int ..] (xrechnungLines xr))
    , concatMap verifySellerIdentifier (zip [1 :: Int ..] sellerIdentifiersList)
    , verifyBuyerIdentifierMaybe (xrechnungBuyer xr)
    ]
  where
    sellerIdentifiersList =
      listNested @"xrechnungSeller" @"sellerIdentifiers" xr

verifyConsistency :: XRechnung -> Validation
verifyConsistency xr =
  concat
    [ check "BR-CO-3"
        ( not $
            existsNested @"xrechnungInvoice" @"invoiceTaxPointDate" xr
              && presentNested @"xrechnungInvoicePeriod" @"invoicePeriodDescriptionCode" xr
        )
        "BT-7 und BT-8 schliessen sich gegenseitig aus."
    , verifyInvoicePeriod (xrechnungInvoicePeriod xr)
    , verifySellerIdentity xr
    , verifyVatPrefixes xr
    ]

verifyPayment :: XRechnung -> Validation
verifyPayment xr =
  case maybeField @"xrechnungPayment" xr of
    Nothing -> []
    Just payment ->
      let code = maybeField @"paymentMeansCode" payment
          hasTransferCode = code == Just "30" || code == Just "58"
          payeeAccounts = fieldValue @"paymentPayeeAccounts" payment
          hasAccounts = not (null payeeAccounts)
          hasCard = existsField @"paymentCard" payment
          hasMandate = existsField @"paymentMandate" payment
       in concat
            [ check "BR-DE-23" (not hasTransferCode || hasAccounts) $
                "Bei BT-81 = 30 oder 58 muss BG-17 vorhanden sein."
            , check "BR-DE-23" (not hasTransferCode || not hasCard) $
                "Bei BT-81 = 30 oder 58 darf BG-18 nicht vorhanden sein."
            , check "BR-DE-23" (not hasTransferCode || not hasMandate) $
                "Bei BT-81 = 30 oder 58 darf BG-19 nicht vorhanden sein."
            , concatMap verifyPayeeAccount (zip [1 :: Int ..] payeeAccounts)
            , case maybeField @"paymentCard" payment of
                Nothing -> []
                Just card ->
                  require "BR-51" "xr.payment.card.primaryAccountNumberId" $
                    presentField @"paymentCardPrimaryAccountNumberId" card
            , case maybeField @"paymentMandate" payment of
                Nothing -> []
                Just mandate ->
                  concat
                    [ require "BT-89" "xr.payment.mandate.id" $
                        presentField @"paymentMandateId" mandate
                    , require "BT-91" "xr.payment.mandate.payerAccountId" $
                        presentField @"paymentMandatePayerAccountId" mandate
                    , check "BT-90" (hasSepaCreditorId xr) $
                        "Bei BG-19 muss BT-90 als sepaCreditorId an Seller oder Payee vorhanden sein."
                    ]
            ]

verifySupportingDocuments :: XRechnung -> Validation
verifySupportingDocuments xr =
  case duplicatesCaseInsensitive filenames of
    [] -> []
    dups ->
      [ validationIssue "BR-DE-22" $
          "Anhaenge mit eingebettetem Inhalt muessen eindeutige Dateinamen haben; doppelt gefunden: "
            <> T.intercalate ", " dups
      ]
  where
    filenames =
      [ filename
      | doc <- fieldValue @"xrechnungSupportingDocuments" xr
      , isPresent (maybeNested @"supportingDocumentEmbedded" @"attachmentContent" doc)
      , Just filename <- [maybeNested @"supportingDocumentEmbedded" @"attachmentFilename" doc]
      ]

verifyVatRules :: XRechnung -> Validation
verifyVatRules xr =
  let categoryInputs = collectCategoryInputs xr
      breakdowns = collectBreakdowns xr
      usedCodes = Set.fromList (map categoryInputCode categoryInputs)
   in concat
        [ concatMap verifyCategoryInput categoryInputs
        , concatMap verifyBreakdownCategory breakdowns
        , verifyBreakdownCoverage categoryInputs breakdowns
        , verifyCategoryIdentityRules xr usedCodes
        , verifyNotSubjectRules xr categoryInputs breakdowns
        ]

verifyTotals :: XRechnung -> Validation
verifyTotals xr =
  case xrechnungTotals xr of
    Nothing -> []
    Just totals ->
      let lineNetTotal = sumRationals $ mapMaybe (fmap decimalRational . lineLineExtensionAmount) (xrechnungLines xr)
          allowanceTotal = sumRationals $ mapMaybe (fmap decimalRational . documentAllowanceChargeAmount) (xrechnungDocumentAllowances xr)
          chargeTotal = sumRationals $ mapMaybe (fmap decimalRational . documentAllowanceChargeAmount) (xrechnungDocumentCharges xr)
          vatTotal = sumRationals $ mapMaybe (fmap decimalRational . vatBreakdownTaxAmount) (xrechnungVatBreakdowns xr)
          prepaid = fromMaybe 0 (decimalRational <$> totalsPrepaidAmount totals)
          roundingAmount = fromMaybe 0 (decimalRational <$> totalsPayableRoundingAmount totals)
       in concat
            [ checkAmountEquals "BR-CO-10" "xr.totals.lineExtensionAmount"
                (totalsLineExtensionAmount totals) lineNetTotal
            , if null (xrechnungDocumentAllowances xr)
                then checkOptionalAmountEquals "BR-CO-11" "xr.totals.allowanceTotalAmount"
                       (totalsAllowanceTotalAmount totals) 0
                else checkAmountEquals "BR-CO-11" "xr.totals.allowanceTotalAmount"
                       (totalsAllowanceTotalAmount totals) allowanceTotal
            , if null (xrechnungDocumentCharges xr)
                then checkOptionalAmountEquals "BR-CO-12" "xr.totals.chargeTotalAmount"
                       (totalsChargeTotalAmount totals) 0
                else checkAmountEquals "BR-CO-12" "xr.totals.chargeTotalAmount"
                       (totalsChargeTotalAmount totals) chargeTotal
            , checkAmountEquals "BR-CO-13" "xr.totals.taxExclusiveAmount"
                (totalsTaxExclusiveAmount totals) (lineNetTotal - allowanceTotal + chargeTotal)
            , checkAmountEquals "BR-CO-14" "xr.totals.taxAmountInDocumentCurrency"
                (totalsTaxAmountInDocumentCurrency totals) vatTotal
            , checkAmountEquals "BR-CO-15" "xr.totals.taxInclusiveAmount"
                (totalsTaxInclusiveAmount totals) $
                  fromMaybe 0 (decimalRational <$> totalsTaxExclusiveAmount totals)
                    + fromMaybe 0 (decimalRational <$> totalsTaxAmountInDocumentCurrency totals)
            , checkAmountEquals "BR-CO-16" "xr.totals.payableAmount"
                (totalsPayableAmount totals) $
                  fromMaybe 0 (decimalRational <$> totalsTaxInclusiveAmount totals)
                    - prepaid
                    + roundingAmount
            , check "BR-CO-25"
                ( maybe False (> 0) (decimalRational <$> totalsPayableAmount totals)
                    <=>
                    ( existsNested @"xrechnungInvoice" @"invoiceDueDate" xr
                        || presentNested @"xrechnungPaymentTerms" @"paymentTermsNote" xr
                    )
                )
                "Bei positivem BT-115 muss BT-9 oder BT-20 vorhanden sein."
            , check "BR-53"
                ( not (existsNested @"xrechnungInvoice" @"invoiceTaxCurrencyCode" xr)
                    || isJust (totalsTaxAmountInTaxCurrency totals)
                )
                "Wenn BT-6 vorhanden ist, muss BT-111 vorhanden sein."
            ]

verifyLines :: XRechnung -> Validation
verifyLines xr =
  concatMap verifyLineDetails (zip [1 :: Int ..] (xrechnungLines xr))

verifyMandatoryVatBreakdown :: (Int, VatBreakdown) -> Validation
verifyMandatoryVatBreakdown (index, breakdown) =
  let prefix = indexedPath "xr.vatBreakdowns" index
   in concat
        [ require "BT-116" (prefix <> ".taxableAmount") (isJust $ vatBreakdownTaxableAmount breakdown)
        , require "BT-117" (prefix <> ".taxAmount") (isJust $ vatBreakdownTaxAmount breakdown)
        , require "BT-118" (prefix <> ".categoryCode") (isPresent $ vatBreakdownCategoryCode breakdown)
        , require "BT-119" (prefix <> ".rate") (isJust $ vatBreakdownRate breakdown)
        ]

verifyMandatoryLine :: (Int, Line) -> Validation
verifyMandatoryLine (index, line) =
  let prefix = indexedPath "xr.lines" index
   in concat
        [ require "BT-126" (prefix <> ".id") (isPresent $ lineId line)
        , require "BT-129" (prefix <> ".quantity") (isJust $ lineQuantity line)
        , require "BT-130" (prefix <> ".quantityUnitCode") (isPresent $ lineQuantityUnitCode line)
        , require "BT-131" (prefix <> ".lineExtensionAmount") (isJust $ lineLineExtensionAmount line)
        , require "BG-29 / BT-146" (prefix <> ".price.netAmount") $
            maybe False (isJust . linePriceNetAmount) (linePrice line)
        , require "BG-30 / BT-151" (prefix <> ".vat.categoryCode") $
            maybe False (isPresent . lineVatCategoryCode) (lineVat line)
        , require "BG-31 / BT-153" (prefix <> ".item.name") $
            maybe False (isPresent . lineItemName) (lineItem line)
        ]

verifyNote :: (Int, Note) -> Validation
verifyNote (index, note) =
  require "BT-22" (indexedPath "xr.notes" index <> ".text") (isPresent $ noteText note)

verifyPrecedingInvoice :: (Int, PrecedingInvoice) -> Validation
verifyPrecedingInvoice (index, invoiceRef) =
  require "BT-25" (indexedPath "xr.precedingInvoices" index <> ".id") (isPresent $ precedingInvoiceId invoiceRef)

verifyPayee :: Maybe Payee -> Validation
verifyPayee Nothing = []
verifyPayee (Just payee) =
  concat
    [ require "BT-59" "xr.payee.name" (presentField @"payeeName" payee)
    , case maybeField @"payeeIdentifier" payee of
        Nothing -> []
        Just identifier ->
          require "BT-60" "xr.payee.identifier.value" (presentField @"identifierValue" identifier)
    ]

verifyTaxRepresentative :: Maybe TaxRepresentative -> Validation
verifyTaxRepresentative Nothing = []
verifyTaxRepresentative (Just representative) =
  concat
    [ require "BT-62" "xr.taxRepresentative.name" (presentField @"taxRepresentativeName" representative)
    , require "BT-63" "xr.taxRepresentative.vatIdentifier" (presentField @"taxRepresentativeVatIdentifier" representative)
    , require "BG-12" "xr.taxRepresentative.address" (existsField @"taxRepresentativeAddress" representative)
    , require "BT-69" "xr.taxRepresentative.address.countryCode" $
        existsNested @"taxRepresentativeAddress" @"addressCountryCode" representative
    ]

verifyDeliveryAddress :: Maybe Delivery -> Validation
verifyDeliveryAddress Nothing = []
verifyDeliveryAddress (Just delivery) =
  case deliveryAddress delivery of
    Nothing -> []
    Just address ->
      concat
        [ require "BR-DE-10 / BT-77" "xr.delivery.address.city" (isPresent $ addressCity address)
        , require "BR-DE-11 / BT-78" "xr.delivery.address.postalCode" (isPresent $ addressPostalCode address)
        , require "BT-80" "xr.delivery.address.countryCode" (isPresent $ addressCountryCode address)
        ]

verifyDocumentAllowance :: (Int, DocumentAllowanceCharge) -> Validation
verifyDocumentAllowance (index, allowance) =
  let prefix = indexedPath "xr.documentAllowances" index
   in concat
        [ require "BT-92" (prefix <> ".amount") (existsField @"documentAllowanceChargeAmount" allowance)
        , require "BT-95" (prefix <> ".tax.categoryCode") $
            presentNested @"documentAllowanceChargeTax" @"taxCategoryCode" allowance
        , check "BR-33 / BR-CO-21"
            ( presentField @"documentAllowanceChargeReason" allowance
                || presentField @"documentAllowanceChargeReasonCode" allowance
            )
            (prefix <> " braucht BT-97 oder BT-98.")
        ]

verifyDocumentCharge :: (Int, DocumentAllowanceCharge) -> Validation
verifyDocumentCharge (index, charge) =
  let prefix = indexedPath "xr.documentCharges" index
   in concat
        [ require "BT-99" (prefix <> ".amount") (existsField @"documentAllowanceChargeAmount" charge)
        , require "BT-102" (prefix <> ".tax.categoryCode") $
            presentNested @"documentAllowanceChargeTax" @"taxCategoryCode" charge
        , check "BR-38 / BR-CO-22"
            ( presentField @"documentAllowanceChargeReason" charge
                || presentField @"documentAllowanceChargeReasonCode" charge
            )
            (prefix <> " braucht BT-104 oder BT-105.")
        ]

verifySupportingDocumentOptional :: (Int, SupportingDocument) -> Validation
verifySupportingDocumentOptional (index, documentRef) =
  let prefix = indexedPath "xr.supportingDocuments" index
      hasBinaryContent =
        isPresent (maybeNested @"supportingDocumentEmbedded" @"attachmentContent" documentRef)
   in concat
        [ require "BT-122" (prefix <> ".id") (presentField @"supportingDocumentId" documentRef)
        , require "BR-DE-22" (prefix <> ".embedded.filename") $
            not hasBinaryContent
              || existsNested @"supportingDocumentEmbedded" @"attachmentFilename" documentRef
        ]

verifyOptionalLineBits :: (Int, Line) -> Validation
verifyOptionalLineBits (index, line) =
  let prefix = indexedPath "xr.lines" index
      itemPrefix = prefix <> ".item"
   in concat
        [ case maybeField @"lineObjectReference" line of
            Nothing -> []
            Just reference ->
              require "BT-128" (prefix <> ".objectReference.id") (presentField @"referenceId" reference)
        , case maybeField @"linePeriod" line of
            Nothing -> []
            Just period ->
              concat
                [ check "BR-CO-20"
                    ( existsField @"linePeriodStartDate" period
                        || existsField @"linePeriodEndDate" period
                    )
                    (prefix <> ".period braucht BT-134 oder BT-135.")
                , check "BR-30"
                    (datesInOrder (maybeField @"linePeriodStartDate" period) (maybeField @"linePeriodEndDate" period))
                    (prefix <> ".period.endDate muss >= startDate sein.")
                ]
        , concatMap (verifyLineAllowance prefix) (zip [1 :: Int ..] (lineAllowances line))
        , concatMap (verifyLineCharge prefix) (zip [1 :: Int ..] (lineCharges line))
        , case maybeField @"lineItem" line of
            Nothing -> []
            Just item ->
              concat
                [ require "BR-64" (itemPrefix <> ".standardIdSchemeId") $
                    not (presentField @"lineItemStandardId" item)
                      || presentField @"lineItemStandardIdSchemeId" item
                , concatMap (verifyClassification itemPrefix) (zip [1 :: Int ..] (lineItemClassifications item))
                , concatMap (verifyItemAttribute itemPrefix) (zip [1 :: Int ..] (lineItemAttributes item))
                ]
        ]

verifySellerIdentifier :: (Int, Identifier) -> Validation
verifySellerIdentifier (index, identifier) =
  require "BT-29" (indexedPath "xr.seller.identifiers" index <> ".value") (isPresent $ identifierValue identifier)

verifyBuyerIdentifierMaybe :: Maybe Buyer -> Validation
verifyBuyerIdentifierMaybe Nothing = []
verifyBuyerIdentifierMaybe (Just buyer) =
  case maybeField @"buyerIdentifier" buyer of
    Nothing -> []
    Just identifier ->
      require "BT-46" "xr.buyer.identifier.value" (presentField @"identifierValue" identifier)

verifyInvoicePeriod :: Maybe InvoicePeriod -> Validation
verifyInvoicePeriod Nothing = []
verifyInvoicePeriod (Just period) =
  check "BR-29"
    (datesInOrder (invoicePeriodStartDate period) (invoicePeriodEndDate period))
    "xr.invoicePeriod.endDate muss >= startDate sein."

verifySellerIdentity :: XRechnung -> Validation
verifySellerIdentity xr =
  let hasSellerIdentifier =
        not (null (listNested @"xrechnungSeller" @"sellerIdentifiers" xr))
          || presentNested @"xrechnungSeller" @"sellerLegalRegistrationId" xr
          || presentNested @"xrechnungSeller" @"sellerVatIdentifier" xr
   in require "BR-CO-26" "xr.seller.identifiers[] oder xr.seller.legalRegistrationId oder xr.seller.vatIdentifier" hasSellerIdentifier

verifyVatPrefixes :: XRechnung -> Validation
verifyVatPrefixes xr =
  concat
    [ maybe [] (validatePrefix "BR-CO-9" "xr.seller.vatIdentifier") (maybeNested @"xrechnungSeller" @"sellerVatIdentifier" xr)
    , maybe [] (validatePrefix "BR-CO-9" "xr.buyer.vatIdentifier") (maybeNested @"xrechnungBuyer" @"buyerVatIdentifier" xr)
    , maybe [] (validatePrefix "BR-CO-9" "xr.taxRepresentative.vatIdentifier") (maybeNested @"xrechnungTaxRepresentative" @"taxRepresentativeVatIdentifier" xr)
    ]

verifyPayeeAccount :: (Int, PayeeAccount) -> Validation
verifyPayeeAccount (index, account) =
  require "BT-84 / BR-50" (indexedPath "xr.payment.payeeAccounts" index <> ".id") (isPresent $ payeeAccountId account)

verifyCategoryInput :: CategoryInput -> Validation
verifyCategoryInput input =
  case categoryInputCode input of
    "S" -> requirePositiveRate "BR-S-5/6/7" input
    "Z" -> requireZeroRate "BR-Z-5/6/7" input
    "E" -> requireZeroRate "BR-E-5/6/7" input
    "AE" -> requireZeroRate "BR-AE-5/6/7" input
    "K" -> requireZeroRate "BR-IC-5/6/7" input
    "G" -> requireZeroRate "BR-G-5/6/7" input
    "O" -> requireMissingRate "BR-O-5/6/7" input
    "L" -> requireNonNegativeRate "BR-IG-5/6/7" input
    "M" -> requireNonNegativeRate "BR-IP-5/6/7" input
    _ -> []

verifyBreakdownCategory :: BreakdownInfo -> Validation
verifyBreakdownCategory breakdown =
  let reasonPresent = breakdownHasReason breakdown
   in case breakdownCode breakdown of
        "S" ->
          concat
            [ requirePositiveBreakdownRate "BR-S-9" breakdown
            , check "BR-S-10" (not reasonPresent) $
                breakdownPath breakdown <> " darf keinen Befreiungsgrund enthalten."
            ]
        "Z" ->
          concat
            [ requireZeroBreakdownRate "BR-Z-9" breakdown
            , check "BR-Z-10" (not reasonPresent) $
                breakdownPath breakdown <> " darf keinen Befreiungsgrund enthalten."
            ]
        "E" ->
          concat
            [ requireZeroBreakdownRate "BR-E-9" breakdown
            , check "BR-E-10" reasonPresent $
                breakdownPath breakdown <> " braucht BT-120 oder BT-121."
            ]
        "AE" ->
          concat
            [ requireZeroBreakdownRate "BR-AE-9" breakdown
            , check "BR-AE-10" reasonPresent $
                breakdownPath breakdown <> " braucht BT-120 oder BT-121."
            ]
        "K" ->
          concat
            [ requireZeroBreakdownRate "BR-IC-9" breakdown
            , check "BR-IC-10" reasonPresent $
                breakdownPath breakdown <> " braucht BT-120 oder BT-121."
            ]
        "G" ->
          concat
            [ requireZeroBreakdownRate "BR-G-9" breakdown
            , check "BR-G-10" reasonPresent $
                breakdownPath breakdown <> " braucht BT-120 oder BT-121."
            ]
        "O" ->
          concat
            [ requireZeroBreakdownRate "BR-O-9" breakdown
            , check "BR-O-10" reasonPresent $
                breakdownPath breakdown <> " braucht BT-120 oder BT-121."
            ]
        "L" ->
          concat
            [ requireNonNegativeBreakdownRate "BR-IG-9" breakdown
            , check "BR-IG-10" (not reasonPresent) $
                breakdownPath breakdown <> " darf keinen Befreiungsgrund enthalten."
            ]
        "M" ->
          concat
            [ requireNonNegativeBreakdownRate "BR-IP-9" breakdown
            , check "BR-IP-10" (not reasonPresent) $
                breakdownPath breakdown <> " darf keinen Befreiungsgrund enthalten."
            ]
        _ -> []

verifyBreakdownCoverage :: [CategoryInput] -> [BreakdownInfo] -> Validation
verifyBreakdownCoverage categoryInputs breakdowns =
  concat
    [ concatMap (requireCodeRateBreakdown "BR-S-1 / BR-S-8" breakdowns) $
        filter ((== "S") . categoryInputCode) categoryInputs
    , concatMap (requireCodeRateBreakdown "BR-IG-1 / BR-IG-8" breakdowns) $
        filter ((== "L") . categoryInputCode) categoryInputs
    , concatMap (requireCodeRateBreakdown "BR-IP-1 / BR-IP-8" breakdowns) $
        filter ((== "M") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-Z-1" breakdowns) $
        filter ((== "Z") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-E-1" breakdowns) $
        filter ((== "E") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-AE-1" breakdowns) $
        filter ((== "AE") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-IC-1" breakdowns) $
        filter ((== "K") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-G-1" breakdowns) $
        filter ((== "G") . categoryInputCode) categoryInputs
    , concatMap (requireSingleCodeBreakdown "BR-O-1" breakdowns) $
        filter ((== "O") . categoryInputCode) categoryInputs
    , concatMap verifyBreakdownAmountFormula breakdowns
    ]

verifyCategoryIdentityRules :: XRechnung -> Set.Set Text -> Validation
verifyCategoryIdentityRules xr usedCodes =
  concat
    [ if any (`Set.member` usedCodes) ["S", "Z", "E", "L", "M"]
        then require "BR-DE-16" "xr.seller.vatIdentifier oder xr.seller.taxIdentifier oder xr.taxRepresentative.vatIdentifier" (hasSellerTaxIdentityGeneral xr)
        else []
    , if "AE" `Set.member` usedCodes
        then concat
          [ require "BR-AE-2/3/4" "Seller-Steuerkennung fuer Reverse Charge" (hasSellerTaxIdentityGeneral xr)
          , require "BR-AE-2/3/4" "xr.buyer.vatIdentifier oder xr.buyer.legalRegistrationId" (hasBuyerTaxIdentityGeneral xr)
          ]
        else []
    , if "K" `Set.member` usedCodes
        then concat
          [ require "BR-IC-2/3/4" "xr.seller.vatIdentifier oder xr.taxRepresentative.vatIdentifier" (hasSellerVatOrRepresentativeVat xr)
          , require "BR-IC-2/3/4" "xr.buyer.vatIdentifier" (hasBuyerVatIdentifier xr)
          , check "BR-IC-11"
              ( hasDeliveryActualDate xr
                  || hasInvoicePeriodDates xr
              )
              "Bei Intra-community supply muss BT-72 oder BG-14 vorhanden sein."
          , check "BR-IC-12" (hasDeliveryCountryCode xr) $
              "Bei Intra-community supply muss BT-80 vorhanden sein."
          ]
        else []
    , if "G" `Set.member` usedCodes
        then require "BR-G-2/3/4" "xr.seller.vatIdentifier oder xr.taxRepresentative.vatIdentifier" (hasSellerVatOrRepresentativeVat xr)
        else []
    ]

verifyNotSubjectRules :: XRechnung -> [CategoryInput] -> [BreakdownInfo] -> Validation
verifyNotSubjectRules xr categoryInputs breakdowns =
  if any ((== "O") . breakdownCode) breakdowns
    then
      concat
        [ check "BR-O-11" (length breakdowns == 1) $
            "Bei BT-118 = O darf es keine weitere Umsatzsteueraufschluesselung geben."
        , check "BR-O-12/13/14"
            (all ((== "O") . categoryInputCode) categoryInputs)
            "Bei BT-118 = O duerfen auch Positionen, Nachlaesse und Abgaben nur Kategorie O verwenden."
        , check "BR-O-2/3/4"
            ( not (hasSellerVatIdentifier xr)
                && not (hasTaxRepresentativeVatIdentifier xr)
                && not (hasBuyerVatIdentifier xr)
            )
            "Bei Kategorie O duerfen BT-31, BT-48 und BT-63 nicht vorhanden sein."
        ]
    else []

verifyBreakdownAmountFormula :: BreakdownInfo -> Validation
verifyBreakdownAmountFormula breakdown =
  case (breakdownTaxableAmount breakdown, breakdownRate breakdown, breakdownTaxAmount breakdown) of
    (Just taxableAmount, Just rate, Just taxAmount) ->
      let expected = roundTo2 $ decimalRational taxableAmount * decimalRational rate / 100
       in check "BR-CO-17"
            (decimalRational taxAmount == expected)
            (breakdownPath breakdown <> ".taxAmount muss BT-116 * BT-119 / 100, auf zwei Nachkommastellen gerundet, entsprechen.")
    _ -> []

verifyLineDetails :: (Int, Line) -> Validation
verifyLineDetails (index, line) =
  let prefix = indexedPath "xr.lines" index
      netAmount = linePriceNetAmount =<< linePrice line
      grossAmount = priceDiscountBaseAmount =<< (linePriceDiscount =<< linePrice line)
      discountAmount = priceDiscountAmount =<< (linePriceDiscount =<< linePrice line)
      baseQuantity = linePriceBaseQuantity =<< linePrice line
      baseQuantityUnit = linePriceBaseQuantityUnitCode =<< linePrice line
      allowances = sumRationals $ mapMaybe (fmap decimalRational . lineAllowanceChargeAmount) (lineAllowances line)
      charges = sumRationals $ mapMaybe (fmap decimalRational . lineAllowanceChargeAmount) (lineCharges line)
      lineQuantityValue = decimalRational <$> lineQuantity line
      baseQuantityValue = maybe 1 decimalRational baseQuantity
      expectedLineAmount =
        roundTo2 $
          fromMaybe 0 lineQuantityValue
            * (fromMaybe 0 (decimalRational <$> netAmount) / baseQuantityValue)
            + charges
            - allowances
   in concat
        [ case netAmount of
            Nothing -> []
            Just amount ->
              check "BR-27" (decimalRational amount >= 0) $
                prefix <> ".price.netAmount darf nicht negativ sein."
        , case grossAmount of
            Nothing -> []
            Just amount ->
              check "BR-28" (decimalRational amount >= 0) $
                prefix <> ".price.discount.baseAmount darf nicht negativ sein."
        , case (netAmount, grossAmount, discountAmount) of
            (Just net', Just gross', Just discount') ->
              check "PEPPOL-EN16931-R046"
                (decimalRational net' == decimalRational gross' - decimalRational discount')
                (prefix <> ".price.netAmount muss BT-148 minus BT-147 entsprechen.")
            _ -> []
        , case baseQuantity of
            Nothing -> []
            Just quantity ->
              check "PEPPOL-EN16931-R121" (decimalRational quantity > 0) $
                prefix <> ".price.baseQuantity muss groesser als 0 sein."
        , case baseQuantityUnit of
            Nothing -> []
            Just unitCode ->
              check "PEPPOL-EN16931-R130"
                (Just unitCode == lineQuantityUnitCode line)
                (prefix <> ".price.baseQuantityUnitCode muss BT-130 entsprechen.")
        , case lineLineExtensionAmount line of
            Nothing -> []
            Just lineAmount ->
              check "PEPPOL-EN16931-R120"
                (decimalRational lineAmount == expectedLineAmount)
                (prefix <> ".lineExtensionAmount muss aus Menge, Preis, Zu-/Abschlaegen berechnet sein.")
        ]

verifyLineAllowance :: Text -> (Int, LineAllowanceCharge) -> Validation
verifyLineAllowance linePrefix (index, allowance) =
  let prefix = linePrefix <> ".allowances[" <> tshow index <> "]"
   in concat
        [ require "BT-136" (prefix <> ".amount") (isJust $ lineAllowanceChargeAmount allowance)
        , check "BR-42 / BR-CO-23"
            ( isPresent (lineAllowanceChargeReason allowance)
                || isPresent (lineAllowanceChargeReasonCode allowance)
            )
            (prefix <> " braucht BT-139 oder BT-140.")
        ]

verifyLineCharge :: Text -> (Int, LineAllowanceCharge) -> Validation
verifyLineCharge linePrefix (index, charge) =
  let prefix = linePrefix <> ".charges[" <> tshow index <> "]"
   in concat
        [ require "BT-141" (prefix <> ".amount") (isJust $ lineAllowanceChargeAmount charge)
        , check "BR-44 / BR-CO-24"
            ( isPresent (lineAllowanceChargeReason charge)
                || isPresent (lineAllowanceChargeReasonCode charge)
            )
            (prefix <> " braucht BT-144 oder BT-145.")
        ]

verifyClassification :: Text -> (Int, Classification) -> Validation
verifyClassification itemPrefix (index, classification) =
  let prefix = itemPrefix <> ".classifications[" <> tshow index <> "]"
   in concat
        [ require "BT-158" (prefix <> ".code") (isPresent $ classificationCode classification)
        , require "BR-65" (prefix <> ".listId") (isPresent $ classificationListId classification)
        ]

verifyItemAttribute :: Text -> (Int, ItemAttribute) -> Validation
verifyItemAttribute itemPrefix (index, attribute) =
  let prefix = itemPrefix <> ".attributes[" <> tshow index <> "]"
   in concat
        [ require "BT-160" (prefix <> ".name") (isPresent $ itemAttributeName attribute)
        , require "BT-161" (prefix <> ".value") (isPresent $ itemAttributeValue attribute)
        ]

collectCategoryInputs :: XRechnung -> [CategoryInput]
collectCategoryInputs xr =
  concat
    [ [ CategoryInput
          (indexedPath "xr.lines" index <> ".vat")
          code
          (lineVatRate vat)
      | (index, line) <- zip [1 :: Int ..] (xrechnungLines xr)
      , Just vat <- [lineVat line]
      , Just code <- [lineVatCategoryCode vat]
      ]
    , [ CategoryInput
          (indexedPath "xr.documentAllowances" index <> ".tax")
          code
          (taxRate tax)
      | (index, allowance) <- zip [1 :: Int ..] (xrechnungDocumentAllowances xr)
      , Just tax <- [documentAllowanceChargeTax allowance]
      , Just code <- [taxCategoryCode tax]
      ]
    , [ CategoryInput
          (indexedPath "xr.documentCharges" index <> ".tax")
          code
          (taxRate tax)
      | (index, charge) <- zip [1 :: Int ..] (xrechnungDocumentCharges xr)
      , Just tax <- [documentAllowanceChargeTax charge]
      , Just code <- [taxCategoryCode tax]
      ]
    ]

collectBreakdowns :: XRechnung -> [BreakdownInfo]
collectBreakdowns xr =
  [ BreakdownInfo
      (indexedPath "xr.vatBreakdowns" index)
      code
      (vatBreakdownTaxableAmount breakdown)
      (vatBreakdownTaxAmount breakdown)
      (vatBreakdownRate breakdown)
      (isPresent (vatBreakdownExemptionReason breakdown) || isPresent (vatBreakdownExemptionReasonCode breakdown))
  | (index, breakdown) <- zip [1 :: Int ..] (xrechnungVatBreakdowns xr)
  , Just code <- [vatBreakdownCategoryCode breakdown]
  ]

requireCodeRateBreakdown :: Text -> [BreakdownInfo] -> CategoryInput -> Validation
requireCodeRateBreakdown rule breakdowns input =
  check rule hasMatch $
    categoryInputPath input <> " braucht eine Umsatzsteueraufschluesselung mit gleichem Code und gleichem Steuersatz."
  where
    hasMatch =
      any
        (\breakdown ->
           breakdownCode breakdown == categoryInputCode input
             && breakdownRate breakdown == categoryInputRate input
        )
        breakdowns

requireSingleCodeBreakdown :: Text -> [BreakdownInfo] -> CategoryInput -> Validation
requireSingleCodeBreakdown rule breakdowns input =
  check rule (matchingCount == 1) $
    categoryInputPath input <> " braucht genau eine Umsatzsteueraufschluesselung fuer Kategorie " <> categoryInputCode input <> "."
  where
    matchingCount =
      length $ filter ((== categoryInputCode input) . breakdownCode) breakdowns

requirePositiveRate :: Text -> CategoryInput -> Validation
requirePositiveRate rule input =
  check rule
    (maybe False ((> 0) . decimalRational) (categoryInputRate input))
    (categoryInputPath input <> ".rate muss groesser als 0 sein.")

requireZeroRate :: Text -> CategoryInput -> Validation
requireZeroRate rule input =
  check rule
    (maybe False ((== 0) . decimalRational) (categoryInputRate input))
    (categoryInputPath input <> ".rate muss 0 sein.")

requireMissingRate :: Text -> CategoryInput -> Validation
requireMissingRate rule input =
  check rule
    (isNothingDecimal $ categoryInputRate input)
    (categoryInputPath input <> ".rate darf nicht vorhanden sein.")

requireNonNegativeRate :: Text -> CategoryInput -> Validation
requireNonNegativeRate rule input =
  check rule
    (maybe False ((>= 0) . decimalRational) (categoryInputRate input))
    (categoryInputPath input <> ".rate muss >= 0 sein.")

requirePositiveBreakdownRate :: Text -> BreakdownInfo -> Validation
requirePositiveBreakdownRate rule breakdown =
  check rule
    (maybe False ((> 0) . decimalRational) (breakdownRate breakdown))
    (breakdownPath breakdown <> ".rate muss groesser als 0 sein.")

requireZeroBreakdownRate :: Text -> BreakdownInfo -> Validation
requireZeroBreakdownRate rule breakdown =
  check rule
    (maybe False ((== 0) . decimalRational) (breakdownRate breakdown))
    (breakdownPath breakdown <> ".rate muss 0 sein.")

requireNonNegativeBreakdownRate :: Text -> BreakdownInfo -> Validation
requireNonNegativeBreakdownRate rule breakdown =
  check rule
    (maybe False ((>= 0) . decimalRational) (breakdownRate breakdown))
    (breakdownPath breakdown <> ".rate muss >= 0 sein.")

hasSellerTaxIdentityGeneral :: XRechnung -> Bool
hasSellerTaxIdentityGeneral xr =
  hasSellerVatIdentifier xr
    || presentNested @"xrechnungSeller" @"sellerTaxIdentifier" xr
    || hasTaxRepresentativeVatIdentifier xr

hasSellerVatOrRepresentativeVat :: XRechnung -> Bool
hasSellerVatOrRepresentativeVat xr =
  hasSellerVatIdentifier xr || hasTaxRepresentativeVatIdentifier xr

hasBuyerTaxIdentityGeneral :: XRechnung -> Bool
hasBuyerTaxIdentityGeneral xr =
  hasBuyerVatIdentifier xr
    || presentNested @"xrechnungBuyer" @"buyerLegalRegistrationId" xr

hasSellerVatIdentifier :: XRechnung -> Bool
hasSellerVatIdentifier xr =
  existsNested @"xrechnungSeller" @"sellerVatIdentifier" xr

hasBuyerVatIdentifier :: XRechnung -> Bool
hasBuyerVatIdentifier xr =
  existsNested @"xrechnungBuyer" @"buyerVatIdentifier" xr

hasTaxRepresentativeVatIdentifier :: XRechnung -> Bool
hasTaxRepresentativeVatIdentifier xr =
  existsNested @"xrechnungTaxRepresentative" @"taxRepresentativeVatIdentifier" xr

hasDeliveryActualDate :: XRechnung -> Bool
hasDeliveryActualDate xr =
  existsNested @"xrechnungDelivery" @"deliveryActualDate" xr

hasDeliveryCountryCode :: XRechnung -> Bool
hasDeliveryCountryCode xr =
  existsNested2 @"xrechnungDelivery" @"deliveryAddress" @"addressCountryCode" xr

hasInvoicePeriodDates :: XRechnung -> Bool
hasInvoicePeriodDates xr =
  existsNested @"xrechnungInvoicePeriod" @"invoicePeriodStartDate" xr
    || existsNested @"xrechnungInvoicePeriod" @"invoicePeriodEndDate" xr

hasSepaCreditorId :: XRechnung -> Bool
hasSepaCreditorId xr =
  presentNested @"xrechnungSeller" @"sellerSepaCreditorId" xr
    || presentNested @"xrechnungPayee" @"payeeSepaCreditorId" xr

validatePrefix :: Text -> Text -> Text -> Validation
validatePrefix rule path vatId =
  check rule (validVatPrefix vatId) $
    path <> " braucht ein ISO-3166-Alpha-2-Praefix (oder EL)."

validVatPrefix :: Text -> Bool
validVatPrefix vatId =
  let upperId = T.toUpper vatId
      prefix = T.take 2 upperId
   in T.length upperId >= 2 && T.all isAsciiUpper prefix

isAsciiUpper :: Char -> Bool
isAsciiUpper c = c >= 'A' && c <= 'Z'

checkAmountEquals :: Text -> Text -> Maybe Decimal -> Rational -> Validation
checkAmountEquals rule path actual expected =
  case actual of
    Nothing -> missing rule path
    Just amount ->
      checkAt rule path (decimalRational amount == expected) "hat einen ungueltigen Wert."

checkOptionalAmountEquals :: Text -> Text -> Maybe Decimal -> Rational -> Validation
checkOptionalAmountEquals _ _ Nothing _ = []
checkOptionalAmountEquals rule path actual expected =
  checkAmountEquals rule path actual expected

decimalRational :: Decimal -> Rational
decimalRational (Decimal value) = value

sumRationals :: [Rational] -> Rational
sumRationals = foldl' (+) 0

roundTo2 :: Rational -> Rational
roundTo2 value = fromInteger (round (value * 100)) % 100

datesInOrder :: Ord a => Maybe a -> Maybe a -> Bool
datesInOrder Nothing _ = True
datesInOrder _ Nothing = True
datesInOrder (Just start) (Just end) = start <= end

require :: Text -> Text -> Bool -> Validation
require rule path present =
  [missingIssue rule path | not present]

requireNonEmpty :: Text -> Text -> [a] -> Validation
requireNonEmpty rule path values =
  require rule path (not (null values))

check :: Text -> Bool -> Text -> Validation
check _ True _ = []
check rule False message
  | T.null message = [validationIssue rule "Regel verletzt."]
  | otherwise = [validationIssue rule message]

checkAt :: Text -> Text -> Bool -> Text -> Validation
checkAt _ _ True _ = []
checkAt rule path False description
  | T.null description = [fieldIssue rule path "ist ungueltig."]
  | otherwise = [fieldIssue rule path description]

indexedPath :: Text -> Int -> Text
indexedPath base index = base <> "[" <> tshow index <> "]"

tshow :: Show a => a -> Text
tshow = T.pack . show

isPresent :: Maybe a -> Bool
isPresent = isJust

isNothingDecimal :: Maybe Decimal -> Bool
isNothingDecimal Nothing = True
isNothingDecimal _ = False

fieldValue :: forall field record value. HasField' field record value => record -> value
fieldValue = getField @field

maybeField :: forall field record value. HasField' field record (Maybe value) => record -> Maybe value
maybeField = getField @field

maybeNested ::
  forall outer inner record parent value.
  (HasField' outer record (Maybe parent), HasField' inner parent (Maybe value)) =>
  record ->
  Maybe value
maybeNested record =
  maybeField @outer record >>= maybeField @inner

maybeNested2 ::
  forall outer middle inner record parent child value.
  ( HasField' outer record (Maybe parent)
  , HasField' middle parent (Maybe child)
  , HasField' inner child (Maybe value)
  ) =>
  record ->
  Maybe value
maybeNested2 record =
  maybeField @outer record
    >>= maybeField @middle
    >>= maybeField @inner

listNested ::
  forall outer inner record parent value.
  (HasField' outer record (Maybe parent), HasField' inner parent [value]) =>
  record ->
  [value]
listNested record =
  maybe [] (fieldValue @inner) (maybeField @outer record)

presentField :: forall field record. HasField' field record (Maybe Text) => record -> Bool
presentField = isPresent . maybeField @field

existsField :: forall field record value. HasField' field record (Maybe value) => record -> Bool
existsField = isJust . maybeField @field

presentNested ::
  forall outer inner record parent.
  (HasField' outer record (Maybe parent), HasField' inner parent (Maybe Text)) =>
  record ->
  Bool
presentNested = isPresent . maybeNested @outer @inner

existsNested ::
  forall outer inner record parent value.
  (HasField' outer record (Maybe parent), HasField' inner parent (Maybe value)) =>
  record ->
  Bool
existsNested = isJust . maybeNested @outer @inner

presentNested2 ::
  forall outer middle inner record parent child.
  ( HasField' outer record (Maybe parent)
  , HasField' middle parent (Maybe child)
  , HasField' inner child (Maybe Text)
  ) =>
  record ->
  Bool
presentNested2 = isPresent . maybeNested2 @outer @middle @inner

existsNested2 ::
  forall outer middle inner record parent child value.
  ( HasField' outer record (Maybe parent)
  , HasField' middle parent (Maybe child)
  , HasField' inner child (Maybe value)
  ) =>
  record ->
  Bool
existsNested2 = isJust . maybeNested2 @outer @middle @inner

validationIssue :: Text -> Text -> ValidationIssue
validationIssue rule description =
  ValidationIssue
    { validationIssueRule = rule
    , validationIssueField = Nothing
    , validationIssueDescription = description
    }

fieldIssue :: Text -> Text -> Text -> ValidationIssue
fieldIssue rule path description =
  ValidationIssue
    { validationIssueRule = rule
    , validationIssueField = Just path
    , validationIssueDescription = description
    }

missingIssue :: Text -> Text -> ValidationIssue
missingIssue rule path = fieldIssue rule path "fehlt."

missing :: Text -> Text -> Validation
missing rule path = [missingIssue rule path]

duplicatesCaseInsensitive :: [Text] -> [Text]
duplicatesCaseInsensitive values =
  go Set.empty Set.empty values
  where
    go _ duplicates [] = Set.toList duplicates
    go seen duplicates (value:rest) =
      let folded = T.pack $ map toLower (T.unpack value)
       in if folded `Set.member` seen
            then go seen (Set.insert value duplicates) rest
            else go (Set.insert folded seen) duplicates rest

infix 1 <=>

(<=>) :: Bool -> Bool -> Bool
left <=> right = not left || right

{-# LANGUAGE OverloadedStrings #-}

module XRechnung.Ubl.Parse
  ( XmlDecodeError (..)
  , renderXmlDecodeError
  , parseUblDocument
  , parseUblXml
  , verifyUblXml
  , verifyUblXmlText
  ) where

import Control.Exception (displayException)
import Control.Monad (unless)
import qualified Data.Map.Strict as Map
import Data.Maybe (catMaybes, fromMaybe, isJust, mapMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import Data.Text.Read (rational)
import Data.Time (Day)
import Data.Time.Format (defaultTimeLocale, parseTimeM)
import Text.XML
  ( Document (..)
  , Element (..)
  , Name (..)
  , Node (..)
  , def
  , parseText
  )
import XRechnung.Model
import XRechnung.Ubl.Internal.Builder (cacNamespace, cbcNamespace, invoiceNamespace)
import XRechnung.Validation (Validation, renderIssues)
import XRechnung.Verify (verify)

data XmlDecodeError = XmlDecodeError
  { xmlDecodeErrorPath :: Text
  , xmlDecodeErrorDescription :: Text
  }
  deriving (Eq, Show)

renderXmlDecodeError :: XmlDecodeError -> Text
renderXmlDecodeError err
  | T.null (xmlDecodeErrorPath err) = xmlDecodeErrorDescription err
  | otherwise = xmlDecodeErrorPath err <> ": " <> xmlDecodeErrorDescription err

parseUblXml :: Text -> Either XmlDecodeError XRechnung
parseUblXml xmlText =
  case parseText def (TL.fromStrict xmlText) of
    Left exc -> decodeError "/Invoice" ("XML konnte nicht geparst werden: " <> T.pack (displayException exc))
    Right document -> parseUblDocument document

parseUblDocument :: Document -> Either XmlDecodeError XRechnung
parseUblDocument document = do
  let root = documentRoot document
      rootPath = "/Invoice"
  unless (matchesName invoiceNamespace "Invoice" root) $
    decodeError rootPath "Erwartet wurde ein UBL-Invoice-Dokument."
  parseInvoice rootPath root

verifyUblXml :: Text -> Either XmlDecodeError Validation
verifyUblXml = fmap verify . parseUblXml

verifyUblXmlText :: Text -> Either XmlDecodeError [Text]
verifyUblXmlText = fmap renderIssues . verifyUblXml

type Decode a = Either XmlDecodeError a

data AdditionalDocumentKind
  = AdditionalDocumentInvoiceObject Reference
  | AdditionalDocumentSupport SupportingDocument

data AmountValue = AmountValue
  { amountValueDecimal :: Maybe Decimal
  , amountValueCurrency :: Maybe Text
  }

data QuantityValue = QuantityValue
  { quantityValueDecimal :: Maybe Decimal
  , quantityValueUnitCode :: Maybe Text
  }

data PartyTaxSchemeInfo = PartyTaxSchemeInfo
  { partyTaxSchemeCode :: Maybe Text
  , partyTaxSchemeCompanyId :: Maybe Text
  }

data ParsedTaxTotal = ParsedTaxTotal
  { parsedTaxTotalPath :: Text
  , parsedTaxTotalAmount :: Maybe Decimal
  , parsedTaxTotalCurrency :: Maybe Text
  , parsedTaxTotalBreakdowns :: [VatBreakdown]
  }

data PriceAllowanceCharge = PriceAllowanceCharge
  { priceAllowanceChargeIsCharge :: Bool
  , priceAllowanceChargeDiscount :: PriceDiscount
  }

parseInvoice :: Text -> Element -> Decode XRechnung
parseInvoice rootPath root = do
  process <- parseProcess rootPath root
  invoice <- parseInvoiceHeader rootPath root
  notes <- traverse (uncurry parseNote) $ indexedElements (childElements cbcNamespace "Note" root) (childPath rootPath "Note")
  invoicePeriod <- parseInvoicePeriod rootPath root
  projectReferenceId <- parseProjectReferenceId rootPath root
  contractReferenceId <- parseQualifiedReferenceId rootPath "ContractDocumentReference" root
  receiptReferenceId <- parseQualifiedReferenceId rootPath "ReceiptDocumentReference" root
  despatchReferenceId <- parseQualifiedReferenceId rootPath "DespatchDocumentReference" root
  originatorReferenceId <- parseQualifiedReferenceId rootPath "OriginatorDocumentReference" root
  orderReference <- parseOrderReference rootPath root
  precedingInvoices <- traverse (uncurry parsePrecedingInvoice) $
    indexedElements (childElements cacNamespace "BillingReference" root) (childPath rootPath "BillingReference")
  additionalDocuments <- traverse (uncurry parseAdditionalDocumentReference) $
    indexedElements (childElements cacNamespace "AdditionalDocumentReference" root) (childPath rootPath "AdditionalDocumentReference")
  invoiceObjectReference <- extractInvoiceObjectReference rootPath additionalDocuments
  seller <- parseSeller rootPath root
  buyer <- parseBuyer rootPath root
  payee <- parsePayee rootPath root
  taxRepresentative <- parseTaxRepresentative rootPath root
  delivery <- parseDelivery rootPath root
  payment <- parsePayment rootPath root
  paymentTerms <- parsePaymentTerms rootPath root
  documentAllowancesAndCharges <- traverse (uncurry parseDocumentAllowanceChargeWrapper) $
    indexedElements (childElements cacNamespace "AllowanceCharge" root) (childPath rootPath "AllowanceCharge")
  let documentAllowances = [entry | (False, entry) <- documentAllowancesAndCharges]
      documentCharges = [entry | (True, entry) <- documentAllowancesAndCharges]
      supportingDocuments = [doc | AdditionalDocumentSupport doc <- additionalDocuments]
  (totals, vatBreakdowns) <- parseTotalsAndBreakdowns rootPath invoice root
  lines_ <- traverse (uncurry parseLine) $
    indexedElements (childElements cacNamespace "InvoiceLine" root) (childPath rootPath "InvoiceLine")
  pure
    XRechnung
      { xrechnungProcess = process
      , xrechnungInvoice = invoice
      , xrechnungNotes = notes
      , xrechnungInvoicePeriod = invoicePeriod
      , xrechnungProjectReferenceId = projectReferenceId
      , xrechnungContractReferenceId = contractReferenceId
      , xrechnungReceiptReferenceId = receiptReferenceId
      , xrechnungDespatchReferenceId = despatchReferenceId
      , xrechnungOriginatorReferenceId = originatorReferenceId
      , xrechnungOrderReference = orderReference
      , xrechnungPrecedingInvoices = precedingInvoices
      , xrechnungInvoiceObjectReference = invoiceObjectReference
      , xrechnungSeller = seller
      , xrechnungBuyer = buyer
      , xrechnungPayee = payee
      , xrechnungTaxRepresentative = taxRepresentative
      , xrechnungDelivery = delivery
      , xrechnungPayment = payment
      , xrechnungPaymentTerms = paymentTerms
      , xrechnungDocumentAllowances = documentAllowances
      , xrechnungDocumentCharges = documentCharges
      , xrechnungTotals = totals
      , xrechnungVatBreakdowns = vatBreakdowns
      , xrechnungSupportingDocuments = supportingDocuments
      , xrechnungLines = lines_
      }

parseProcess :: Text -> Element -> Decode (Maybe Process)
parseProcess rootPath root = do
  customizationId <- optionalTextChild rootPath cbcNamespace "CustomizationID" root
  profileId <- optionalTextChild rootPath cbcNamespace "ProfileID" root
  pure $
    if any isJust [customizationId, profileId]
      then Just Process {processCustomizationId = customizationId, processProfileId = profileId}
      else Nothing

parseInvoiceHeader :: Text -> Element -> Decode (Maybe Invoice)
parseInvoiceHeader rootPath root = do
  invoiceId <- optionalTextChild rootPath cbcNamespace "ID" root
  issueDate <- optionalDayChild rootPath "IssueDate" root
  dueDate <- optionalDayChild rootPath "DueDate" root
  typeCode <- optionalTextChild rootPath cbcNamespace "InvoiceTypeCode" root
  documentCurrencyCode <- optionalTextChild rootPath cbcNamespace "DocumentCurrencyCode" root
  taxCurrencyCode <- optionalTextChild rootPath cbcNamespace "TaxCurrencyCode" root
  taxPointDate <- optionalDayChild rootPath "TaxPointDate" root
  buyerReference <- optionalTextChild rootPath cbcNamespace "BuyerReference" root
  accountingCost <- optionalTextChild rootPath cbcNamespace "AccountingCost" root
  pure $
    if or
      [ isJust invoiceId
      , isJust issueDate
      , isJust dueDate
      , isJust typeCode
      , isJust documentCurrencyCode
      , isJust taxCurrencyCode
      , isJust taxPointDate
      , isJust buyerReference
      , isJust accountingCost
      ]
      then
        Just
          Invoice
            { invoiceId = invoiceId
            , invoiceIssueDate = issueDate
            , invoiceDueDate = dueDate
            , invoiceTypeCode = typeCode
            , invoiceDocumentCurrencyCode = documentCurrencyCode
            , invoiceTaxCurrencyCode = taxCurrencyCode
            , invoiceTaxPointDate = taxPointDate
            , invoiceBuyerReference = buyerReference
            , invoiceAccountingCost = accountingCost
            }
      else Nothing

parseNote :: Int -> Element -> Decode Note
parseNote _ noteElement =
  pure $
    case noteTextValue of
      Nothing -> Note {noteSubjectCode = Nothing, noteText = Nothing}
      Just rawText ->
        case splitSubjectCode rawText of
          Just (subjectCode, body) ->
            Note
              { noteSubjectCode = Just subjectCode
              , noteText = Just body
              }
          Nothing ->
            Note
              { noteSubjectCode = Nothing
              , noteText = Just rawText
              }
  where
    noteTextValue = elementText noteElement

parseInvoicePeriod :: Text -> Element -> Decode (Maybe InvoicePeriod)
parseInvoicePeriod rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "InvoicePeriod" root
  traverse (parseInvoicePeriodElement (childPath rootPath "InvoicePeriod")) wrapper

parseInvoicePeriodElement :: Text -> Element -> Decode InvoicePeriod
parseInvoicePeriodElement periodPath periodElement = do
  startDate <- optionalDayChild periodPath "StartDate" periodElement
  endDate <- optionalDayChild periodPath "EndDate" periodElement
  descriptionCode <- optionalTextChild periodPath cbcNamespace "DescriptionCode" periodElement
  pure
    InvoicePeriod
      { invoicePeriodDescriptionCode = descriptionCode
      , invoicePeriodStartDate = startDate
      , invoicePeriodEndDate = endDate
      }

parseProjectReferenceId :: Text -> Element -> Decode (Maybe Text)
parseProjectReferenceId rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "ProjectReference" root
  maybe (pure Nothing) (optionalTextChild (childPath rootPath "ProjectReference") cbcNamespace "ID") wrapper

parseQualifiedReferenceId :: Text -> Text -> Element -> Decode (Maybe Text)
parseQualifiedReferenceId rootPath wrapperName root = do
  wrapper <- optionalChildElement rootPath cacNamespace wrapperName root
  maybe (pure Nothing) (optionalTextChild (childPath rootPath wrapperName) cbcNamespace "ID") wrapper

parseOrderReference :: Text -> Element -> Decode (Maybe OrderReference)
parseOrderReference rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "OrderReference" root
  traverse (parseOrderReferenceElement (childPath rootPath "OrderReference")) wrapper

parseOrderReferenceElement :: Text -> Element -> Decode OrderReference
parseOrderReferenceElement referencePath referenceElement = do
  referenceId <- optionalTextChild referencePath cbcNamespace "ID" referenceElement
  salesOrderId <- optionalTextChild referencePath cbcNamespace "SalesOrderID" referenceElement
  pure
    OrderReference
      { orderReferenceId = referenceId
      , orderReferenceSalesOrderId = salesOrderId
      }

parsePrecedingInvoice :: Int -> Element -> Decode PrecedingInvoice
parsePrecedingInvoice index billingReferenceElement = do
  let referencePath = indexedPath (childPath "/Invoice" "BillingReference") index
  invoiceDocumentReference <- optionalChildElement referencePath cacNamespace "InvoiceDocumentReference" billingReferenceElement
  case invoiceDocumentReference of
    Nothing ->
      pure
        PrecedingInvoice
          { precedingInvoiceId = Nothing
          , precedingInvoiceIssueDate = Nothing
          }
    Just invoiceRefElement -> do
      let invoiceRefPath = childPath referencePath "InvoiceDocumentReference"
      referenceId <- optionalTextChild invoiceRefPath cbcNamespace "ID" invoiceRefElement
      issueDate <- optionalDayChild invoiceRefPath "IssueDate" invoiceRefElement
      pure
        PrecedingInvoice
          { precedingInvoiceId = referenceId
          , precedingInvoiceIssueDate = issueDate
          }

parseAdditionalDocumentReference :: Int -> Element -> Decode AdditionalDocumentKind
parseAdditionalDocumentReference index referenceElement = do
  let referencePath = indexedPath (childPath "/Invoice" "AdditionalDocumentReference") index
  identifier <- optionalIdentifierChild referencePath "ID" referenceElement
  description <- optionalTextChild referencePath cbcNamespace "DocumentDescription" referenceElement
  documentTypeCode <- optionalTextChild referencePath cbcNamespace "DocumentTypeCode" referenceElement
  attachment <- optionalChildElement referencePath cacNamespace "Attachment" referenceElement >>= traverse (parseAttachment (childPath referencePath "Attachment"))
  let reference =
        Reference
          { referenceId = identifierValue =<< identifier
          , referenceSchemeId = identifierSchemeId =<< identifier
          }
      isInvoiceObjectReference =
        documentTypeCode == Just "130"
          && description == Nothing
          && attachment == Nothing
  pure $
    if isInvoiceObjectReference
      then AdditionalDocumentInvoiceObject reference
      else
        AdditionalDocumentSupport
          SupportingDocument
            { supportingDocumentId = identifierValue =<< identifier
            , supportingDocumentDescription = description
            , supportingDocumentEmbedded = attachment
            }

extractInvoiceObjectReference :: Text -> [AdditionalDocumentKind] -> Decode (Maybe Reference)
extractInvoiceObjectReference rootPath additionalDocuments =
  case [reference | AdditionalDocumentInvoiceObject reference <- additionalDocuments] of
    [] -> pure Nothing
    [reference] -> pure (Just reference)
    _ -> decodeError (childPath rootPath "AdditionalDocumentReference") "Mehrere Invoice-Object-References koennen im Modell nicht abgebildet werden."

parseSeller :: Text -> Element -> Decode (Maybe Seller)
parseSeller rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "AccountingSupplierParty" root
  traverse (parseSellerWrapper (childPath rootPath "AccountingSupplierParty")) wrapper

parseSellerWrapper :: Text -> Element -> Decode Seller
parseSellerWrapper wrapperPath wrapperElement = do
  party <- optionalChildElement wrapperPath cacNamespace "Party" wrapperElement
  let partyPath = childPath wrapperPath "Party"
  endpoint <- optionalIdentifierChildMaybe partyPath "EndpointID" party
  partyIdentifiers <- traverse (uncurry parsePartyIdentification) $
    indexedElements (childElementsMaybe cacNamespace "PartyIdentification" party) (childPath partyPath "PartyIdentification")
  sellerSepaCreditorId <- extractSepaCreditorId (childPath partyPath "PartyIdentification") partyIdentifiers
  tradeName <- parsePartyName partyPath party
  address <- optionalChildElementMaybe partyPath cacNamespace "PostalAddress" party >>= traverse (parseAddress (childPath partyPath "PostalAddress"))
  contact <- optionalChildElementMaybe partyPath cacNamespace "Contact" party >>= traverse (parseContact (childPath partyPath "Contact"))
  legalEntity <- optionalChildElementMaybe partyPath cacNamespace "PartyLegalEntity" party
  sellerName <- maybe (pure Nothing) (optionalTextChild (childPath partyPath "PartyLegalEntity") cbcNamespace "RegistrationName") legalEntity
  legalRegistration <- maybe (pure Nothing) (optionalIdentifierChild (childPath partyPath "PartyLegalEntity") "CompanyID") legalEntity
  legalForm <- maybe (pure Nothing) (optionalTextChild (childPath partyPath "PartyLegalEntity") cbcNamespace "CompanyLegalForm") legalEntity
  partyTaxSchemes <- traverse (uncurry parsePartyTaxScheme) $
    indexedElements (childElementsMaybe cacNamespace "PartyTaxScheme" party) (childPath partyPath "PartyTaxScheme")
  sellerVatIdentifier <- uniquePartyTaxCompanyId (childPath partyPath "PartyTaxScheme") "VAT" partyTaxSchemes
  sellerTaxIdentifier <- uniquePartyTaxCompanyId (childPath partyPath "PartyTaxScheme") "TAX" partyTaxSchemes
  pure
    Seller
      { sellerName = sellerName
      , sellerTradeName = tradeName
      , sellerEndpoint = endpoint
      , sellerIdentifiers = filter (not . isSepaIdentifier) partyIdentifiers
      , sellerSepaCreditorId = sellerSepaCreditorId
      , sellerVatIdentifier = sellerVatIdentifier
      , sellerTaxIdentifier = sellerTaxIdentifier
      , sellerLegalRegistrationId = identifierValue =<< legalRegistration
      , sellerLegalRegistrationIdSchemeId = identifierSchemeId =<< legalRegistration
      , sellerLegalForm = legalForm
      , sellerAddress = address
      , sellerContact = contact
      }

parseBuyer :: Text -> Element -> Decode (Maybe Buyer)
parseBuyer rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "AccountingCustomerParty" root
  traverse (parseBuyerWrapper (childPath rootPath "AccountingCustomerParty")) wrapper

parseBuyerWrapper :: Text -> Element -> Decode Buyer
parseBuyerWrapper wrapperPath wrapperElement = do
  party <- optionalChildElement wrapperPath cacNamespace "Party" wrapperElement
  let partyPath = childPath wrapperPath "Party"
  endpoint <- optionalIdentifierChildMaybe partyPath "EndpointID" party
  partyIdentifiers <- traverse (uncurry parsePartyIdentification) $
    indexedElements (childElementsMaybe cacNamespace "PartyIdentification" party) (childPath partyPath "PartyIdentification")
  buyerIdentifier <- uniqueIdentifier (childPath partyPath "PartyIdentification") partyIdentifiers
  tradeName <- parsePartyName partyPath party
  address <- optionalChildElementMaybe partyPath cacNamespace "PostalAddress" party >>= traverse (parseAddress (childPath partyPath "PostalAddress"))
  contact <- optionalChildElementMaybe partyPath cacNamespace "Contact" party >>= traverse (parseContact (childPath partyPath "Contact"))
  legalEntity <- optionalChildElementMaybe partyPath cacNamespace "PartyLegalEntity" party
  buyerName <- maybe (pure Nothing) (optionalTextChild (childPath partyPath "PartyLegalEntity") cbcNamespace "RegistrationName") legalEntity
  legalRegistration <- maybe (pure Nothing) (optionalIdentifierChild (childPath partyPath "PartyLegalEntity") "CompanyID") legalEntity
  partyTaxSchemes <- traverse (uncurry parsePartyTaxScheme) $
    indexedElements (childElementsMaybe cacNamespace "PartyTaxScheme" party) (childPath partyPath "PartyTaxScheme")
  buyerVatIdentifier <- uniquePartyTaxCompanyId (childPath partyPath "PartyTaxScheme") "VAT" partyTaxSchemes
  pure
    Buyer
      { buyerName = buyerName
      , buyerTradeName = tradeName
      , buyerEndpoint = endpoint
      , buyerIdentifier = buyerIdentifier
      , buyerVatIdentifier = buyerVatIdentifier
      , buyerLegalRegistrationId = identifierValue =<< legalRegistration
      , buyerLegalRegistrationIdSchemeId = identifierSchemeId =<< legalRegistration
      , buyerAddress = address
      , buyerContact = contact
      }

parsePayee :: Text -> Element -> Decode (Maybe Payee)
parsePayee rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "PayeeParty" root
  traverse (parsePayeeElement (childPath rootPath "PayeeParty")) wrapper

parsePayeeElement :: Text -> Element -> Decode Payee
parsePayeeElement payeePath payeeElement = do
  partyIdentifiers <- traverse (uncurry parsePartyIdentification) $
    indexedElements (childElements cacNamespace "PartyIdentification" payeeElement) (childPath payeePath "PartyIdentification")
  payeeSepaCreditorId <- extractSepaCreditorId (childPath payeePath "PartyIdentification") partyIdentifiers
  payeeIdentifier <- uniqueIdentifier (childPath payeePath "PartyIdentification") (filter (not . isSepaIdentifier) partyIdentifiers)
  payeeName <- parsePartyName payeePath (Just payeeElement)
  legalEntity <- optionalChildElement payeePath cacNamespace "PartyLegalEntity" payeeElement
  legalRegistration <- maybe (pure Nothing) (optionalIdentifierChild (childPath payeePath "PartyLegalEntity") "CompanyID") legalEntity
  pure
    Payee
      { payeeName = payeeName
      , payeeIdentifier = payeeIdentifier
      , payeeSepaCreditorId = payeeSepaCreditorId
      , payeeLegalRegistrationId = identifierValue =<< legalRegistration
      , payeeLegalRegistrationIdSchemeId = identifierSchemeId =<< legalRegistration
      }

parseTaxRepresentative :: Text -> Element -> Decode (Maybe TaxRepresentative)
parseTaxRepresentative rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "TaxRepresentativeParty" root
  traverse (parseTaxRepresentativeElement (childPath rootPath "TaxRepresentativeParty")) wrapper

parseTaxRepresentativeElement :: Text -> Element -> Decode TaxRepresentative
parseTaxRepresentativeElement representativePath representativeElement = do
  representativeName <- parsePartyName representativePath (Just representativeElement)
  representativeAddress <- optionalChildElement representativePath cacNamespace "PostalAddress" representativeElement >>= traverse (parseAddress (childPath representativePath "PostalAddress"))
  taxSchemes <- traverse (uncurry parsePartyTaxScheme) $
    indexedElements (childElements cacNamespace "PartyTaxScheme" representativeElement) (childPath representativePath "PartyTaxScheme")
  representativeVatIdentifier <- uniquePartyTaxCompanyId (childPath representativePath "PartyTaxScheme") "VAT" taxSchemes
  pure
    TaxRepresentative
      { taxRepresentativeName = representativeName
      , taxRepresentativeVatIdentifier = representativeVatIdentifier
      , taxRepresentativeAddress = representativeAddress
      }

parseDelivery :: Text -> Element -> Decode (Maybe Delivery)
parseDelivery rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "Delivery" root
  traverse (parseDeliveryElement (childPath rootPath "Delivery")) wrapper

parseDeliveryElement :: Text -> Element -> Decode Delivery
parseDeliveryElement deliveryPath deliveryElement = do
  actualDate <- optionalDayChild deliveryPath "ActualDeliveryDate" deliveryElement
  deliveryLocationElement <- optionalChildElement deliveryPath cacNamespace "DeliveryLocation" deliveryElement
  deliveryLocation <- case deliveryLocationElement of
    Nothing -> pure Nothing
    Just locationElement -> do
      locationIdentifier <- optionalIdentifierChild (childPath deliveryPath "DeliveryLocation") "ID" locationElement
      pure $ fmap identifierToReference locationIdentifier
  deliveryAddress <- case deliveryLocationElement of
    Nothing -> pure Nothing
    Just locationElement ->
      optionalChildElement (childPath deliveryPath "DeliveryLocation") cacNamespace "Address" locationElement
        >>= traverse (parseAddress (childPath (childPath deliveryPath "DeliveryLocation") "Address"))
  deliveryPartyElement <- optionalChildElement deliveryPath cacNamespace "DeliveryParty" deliveryElement
  deliveryPartyName <- parsePartyName (childPath deliveryPath "DeliveryParty") deliveryPartyElement
  pure
    Delivery
      { deliveryPartyName = deliveryPartyName
      , deliveryActualDate = actualDate
      , deliveryLocation = deliveryLocation
      , deliveryAddress = deliveryAddress
      }

parsePayment :: Text -> Element -> Decode (Maybe Payment)
parsePayment rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "PaymentMeans" root
  traverse (parsePaymentElement (childPath rootPath "PaymentMeans")) wrapper

parsePaymentElement :: Text -> Element -> Decode Payment
parsePaymentElement paymentPath paymentElement = do
  paymentMeansCodeElement <- optionalChildElement paymentPath cbcNamespace "PaymentMeansCode" paymentElement
  paymentMeansCode <- traverse (pure . elementText) paymentMeansCodeElement
  paymentMeansText <- pure $ paymentMeansCodeElement >>= lookupAttr "name"
  paymentId <- optionalTextChild paymentPath cbcNamespace "PaymentID" paymentElement
  payeeAccounts <- traverse (uncurry parsePayeeFinancialAccount) $
    indexedElements (childElements cacNamespace "PayeeFinancialAccount" paymentElement) (childPath paymentPath "PayeeFinancialAccount")
  paymentCard <- optionalChildElement paymentPath cacNamespace "CardAccount" paymentElement >>= traverse (parsePaymentCard (childPath paymentPath "CardAccount"))
  paymentMandate <- optionalChildElement paymentPath cacNamespace "PaymentMandate" paymentElement >>= traverse (parsePaymentMandate (childPath paymentPath "PaymentMandate"))
  pure
    Payment
      { paymentMeansCode = joinMaybe paymentMeansCode
      , paymentMeansText = paymentMeansText
      , paymentPaymentId = paymentId
      , paymentPayeeAccounts = payeeAccounts
      , paymentCard = paymentCard
      , paymentMandate = paymentMandate
      }

parsePayeeFinancialAccount :: Int -> Element -> Decode PayeeAccount
parsePayeeFinancialAccount index accountElement = do
  let accountPath = indexedPath (childPath "/Invoice/PaymentMeans" "PayeeFinancialAccount") index
  accountId <- optionalTextChild accountPath cbcNamespace "ID" accountElement
  accountName <- optionalTextChild accountPath cbcNamespace "Name" accountElement
  branch <- optionalChildElement accountPath cacNamespace "FinancialInstitutionBranch" accountElement
  bic <- maybe (pure Nothing) (optionalTextChild (childPath accountPath "FinancialInstitutionBranch") cbcNamespace "ID") branch
  pure
    PayeeAccount
      { payeeAccountId = accountId
      , payeeAccountName = accountName
      , payeeAccountBic = bic
      }

parsePaymentCard :: Text -> Element -> Decode PaymentCard
parsePaymentCard cardPath cardElement = do
  primaryAccountNumberId <- optionalTextChild cardPath cbcNamespace "PrimaryAccountNumberID" cardElement
  holderName <- optionalTextChild cardPath cbcNamespace "HolderName" cardElement
  pure
    PaymentCard
      { paymentCardPrimaryAccountNumberId = primaryAccountNumberId
      , paymentCardHolderName = holderName
      }

parsePaymentMandate :: Text -> Element -> Decode PaymentMandate
parsePaymentMandate mandatePath mandateElement = do
  mandateId <- optionalTextChild mandatePath cbcNamespace "ID" mandateElement
  payerFinancialAccount <- optionalChildElement mandatePath cacNamespace "PayerFinancialAccount" mandateElement
  payerAccountId <- maybe (pure Nothing) (optionalTextChild (childPath mandatePath "PayerFinancialAccount") cbcNamespace "ID") payerFinancialAccount
  pure
    PaymentMandate
      { paymentMandateId = mandateId
      , paymentMandatePayerAccountId = payerAccountId
      }

parsePaymentTerms :: Text -> Element -> Decode (Maybe PaymentTerms)
parsePaymentTerms rootPath root = do
  wrapper <- optionalChildElement rootPath cacNamespace "PaymentTerms" root
  traverse (parsePaymentTermsElement (childPath rootPath "PaymentTerms")) wrapper

parsePaymentTermsElement :: Text -> Element -> Decode PaymentTerms
parsePaymentTermsElement termsPath termsElement = do
  note <- optionalTextChild termsPath cbcNamespace "Note" termsElement
  pure PaymentTerms {paymentTermsNote = note}

parseDocumentAllowanceChargeWrapper :: Int -> Element -> Decode (Bool, DocumentAllowanceCharge)
parseDocumentAllowanceChargeWrapper index entryElement = do
  let entryPath = indexedPath (childPath "/Invoice" "AllowanceCharge") index
  chargeIndicator <- requiredBoolChild entryPath "ChargeIndicator" entryElement
  reasonCode <- optionalTextChild entryPath cbcNamespace "AllowanceChargeReasonCode" entryElement
  reasonText <- optionalTextChild entryPath cbcNamespace "AllowanceChargeReason" entryElement
  percent <- optionalDecimalChild entryPath "MultiplierFactorNumeric" entryElement
  amount <- fmap amountValueDecimal <$> optionalAmountChild entryPath "Amount" entryElement
  baseAmount <- fmap amountValueDecimal <$> optionalAmountChild entryPath "BaseAmount" entryElement
  taxCategory <- optionalChildElement entryPath cacNamespace "TaxCategory" entryElement >>= traverse (parseTax (childPath entryPath "TaxCategory"))
  pure
    ( chargeIndicator
    , DocumentAllowanceCharge
        { documentAllowanceChargeAmount = joinMaybe amount
        , documentAllowanceChargeBaseAmount = joinMaybe baseAmount
        , documentAllowanceChargePercent = percent
        , documentAllowanceChargeReason = reasonText
        , documentAllowanceChargeReasonCode = reasonCode
        , documentAllowanceChargeTax = taxCategory
        }
    )

parseTotalsAndBreakdowns :: Text -> Maybe Invoice -> Element -> Decode (Maybe Totals, [VatBreakdown])
parseTotalsAndBreakdowns rootPath maybeInvoice root = do
  taxTotals <- traverse (uncurry parseTaxTotal) $
    indexedElements (childElements cacNamespace "TaxTotal" root) (childPath rootPath "TaxTotal")
  monetaryTotal <- optionalChildElement rootPath cacNamespace "LegalMonetaryTotal" root >>= traverse (parseMonetaryTotal (childPath rootPath "LegalMonetaryTotal"))
  let documentCurrency = maybeInvoice >>= invoiceDocumentCurrencyCode
      taxCurrency = maybeInvoice >>= invoiceTaxCurrencyCode
  (taxAmountInDocumentCurrency, taxAmountInTaxCurrency, vatBreakdowns) <-
    classifyTaxTotals rootPath documentCurrency taxCurrency taxTotals
  let totalsPresent = isJust monetaryTotal || not (null taxTotals)
  pure
    ( if totalsPresent
        then
          Just
            Totals
              { totalsLineExtensionAmount = monetaryTotal >>= totalsLineExtensionAmount
              , totalsAllowanceTotalAmount = monetaryTotal >>= totalsAllowanceTotalAmount
              , totalsChargeTotalAmount = monetaryTotal >>= totalsChargeTotalAmount
              , totalsTaxExclusiveAmount = monetaryTotal >>= totalsTaxExclusiveAmount
              , totalsTaxAmountInDocumentCurrency = taxAmountInDocumentCurrency
              , totalsTaxAmountInTaxCurrency = taxAmountInTaxCurrency
              , totalsTaxInclusiveAmount = monetaryTotal >>= totalsTaxInclusiveAmount
              , totalsPrepaidAmount = monetaryTotal >>= totalsPrepaidAmount
              , totalsPayableRoundingAmount = monetaryTotal >>= totalsPayableRoundingAmount
              , totalsPayableAmount = monetaryTotal >>= totalsPayableAmount
              }
        else Nothing
    , vatBreakdowns
    )

parseTaxTotal :: Int -> Element -> Decode ParsedTaxTotal
parseTaxTotal index taxTotalElement = do
  let taxTotalPath = indexedPath (childPath "/Invoice" "TaxTotal") index
  taxAmount <- optionalAmountChild taxTotalPath "TaxAmount" taxTotalElement
  subtotals <- traverse (uncurry parseVatBreakdown) $
    indexedElements (childElements cacNamespace "TaxSubtotal" taxTotalElement) (childPath taxTotalPath "TaxSubtotal")
  pure
    ParsedTaxTotal
      { parsedTaxTotalPath = taxTotalPath
      , parsedTaxTotalAmount = amountValueDecimal =<< taxAmount
      , parsedTaxTotalCurrency = amountValueCurrency =<< taxAmount
      , parsedTaxTotalBreakdowns = subtotals
      }

classifyTaxTotals :: Text -> Maybe Text -> Maybe Text -> [ParsedTaxTotal] -> Decode (Maybe Decimal, Maybe Decimal, [VatBreakdown])
classifyTaxTotals rootPath documentCurrency taxCurrency parsedTaxTotals =
  case withBreakdowns of
    [] -> classifyTaxTotalsWithoutBreakdowns rootPath documentCurrency taxCurrency withoutBreakdowns
    [documentTaxTotal] ->
      case withoutBreakdowns of
        [] ->
          pure
            ( parsedTaxTotalAmount documentTaxTotal
            , Nothing
            , parsedTaxTotalBreakdowns documentTaxTotal
            )
        [accountingTaxTotal] ->
          pure
            ( parsedTaxTotalAmount documentTaxTotal
            , parsedTaxTotalAmount accountingTaxTotal
            , parsedTaxTotalBreakdowns documentTaxTotal
            )
        _ ->
          decodeError (childPath rootPath "TaxTotal") "Mehrere zusaetzliche TaxTotal-Eintraege ohne TaxSubtotal werden vom Modell nicht abgebildet."
    _ ->
      decodeError (childPath rootPath "TaxTotal") "Mehrere TaxTotal-Eintraege mit TaxSubtotal werden vom Modell nicht abgebildet."
  where
    withBreakdowns = filter (not . null . parsedTaxTotalBreakdowns) parsedTaxTotals
    withoutBreakdowns = filter (null . parsedTaxTotalBreakdowns) parsedTaxTotals

classifyTaxTotalsWithoutBreakdowns :: Text -> Maybe Text -> Maybe Text -> [ParsedTaxTotal] -> Decode (Maybe Decimal, Maybe Decimal, [VatBreakdown])
classifyTaxTotalsWithoutBreakdowns rootPath documentCurrency taxCurrency parsedTaxTotals =
  case parsedTaxTotals of
    [] -> pure (Nothing, Nothing, [])
    [singleTaxTotal] -> pure (parsedTaxTotalAmount singleTaxTotal, Nothing, [])
    [firstTaxTotal, secondTaxTotal]
      | isDistinctCurrency ->
          case (parsedTaxTotalCurrency firstTaxTotal, parsedTaxTotalCurrency secondTaxTotal) of
            (Just firstCurrency, Just secondCurrency)
              | firstCurrency == fromMaybe firstCurrency documentCurrency
                  && secondCurrency == fromMaybe secondCurrency taxCurrency ->
                  pure (parsedTaxTotalAmount firstTaxTotal, parsedTaxTotalAmount secondTaxTotal, [])
              | secondCurrency == fromMaybe secondCurrency documentCurrency
                  && firstCurrency == fromMaybe firstCurrency taxCurrency ->
                  pure (parsedTaxTotalAmount secondTaxTotal, parsedTaxTotalAmount firstTaxTotal, [])
            _ ->
              decodeError (childPath rootPath "TaxTotal") "Mehrere TaxTotal-Eintraege ohne TaxSubtotal sind ohne Waehrungszuordnung mehrdeutig."
    _ ->
      decodeError (childPath rootPath "TaxTotal") "Mehrere TaxTotal-Eintraege ohne TaxSubtotal werden vom Modell nicht abgebildet."
  where
    isDistinctCurrency =
      case (documentCurrency, taxCurrency) of
        (Just docCurrency, Just taxCurrencyCode) -> docCurrency /= taxCurrencyCode
        _ -> False

parseMonetaryTotal :: Text -> Element -> Decode Totals
parseMonetaryTotal totalsPath totalsElement = do
  lineExtensionAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "LineExtensionAmount" totalsElement
  taxExclusiveAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "TaxExclusiveAmount" totalsElement
  taxInclusiveAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "TaxInclusiveAmount" totalsElement
  allowanceTotalAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "AllowanceTotalAmount" totalsElement
  chargeTotalAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "ChargeTotalAmount" totalsElement
  prepaidAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "PrepaidAmount" totalsElement
  payableRoundingAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "PayableRoundingAmount" totalsElement
  payableAmount <- fmap amountValueDecimal <$> optionalAmountChild totalsPath "PayableAmount" totalsElement
  pure
    Totals
      { totalsLineExtensionAmount = joinMaybe lineExtensionAmount
      , totalsAllowanceTotalAmount = joinMaybe allowanceTotalAmount
      , totalsChargeTotalAmount = joinMaybe chargeTotalAmount
      , totalsTaxExclusiveAmount = joinMaybe taxExclusiveAmount
      , totalsTaxAmountInDocumentCurrency = Nothing
      , totalsTaxAmountInTaxCurrency = Nothing
      , totalsTaxInclusiveAmount = joinMaybe taxInclusiveAmount
      , totalsPrepaidAmount = joinMaybe prepaidAmount
      , totalsPayableRoundingAmount = joinMaybe payableRoundingAmount
      , totalsPayableAmount = joinMaybe payableAmount
      }

parseVatBreakdown :: Int -> Element -> Decode VatBreakdown
parseVatBreakdown index subtotalElement = do
  let subtotalPath = indexedPath (childPath "/Invoice/TaxTotal" "TaxSubtotal") index
  taxableAmount <- fmap amountValueDecimal <$> optionalAmountChild subtotalPath "TaxableAmount" subtotalElement
  taxAmount <- fmap amountValueDecimal <$> optionalAmountChild subtotalPath "TaxAmount" subtotalElement
  category <- optionalChildElement subtotalPath cacNamespace "TaxCategory" subtotalElement
  (categoryCode, rate, exemptionReason, exemptionReasonCode) <-
    case category of
      Nothing -> pure (Nothing, Nothing, Nothing, Nothing)
      Just categoryElement -> parseTaxCategoryFields (childPath subtotalPath "TaxCategory") categoryElement
  pure
    VatBreakdown
      { vatBreakdownTaxableAmount = joinMaybe taxableAmount
      , vatBreakdownTaxAmount = joinMaybe taxAmount
      , vatBreakdownCategoryCode = categoryCode
      , vatBreakdownRate = rate
      , vatBreakdownExemptionReason = exemptionReason
      , vatBreakdownExemptionReasonCode = exemptionReasonCode
      }

parseLine :: Int -> Element -> Decode Line
parseLine index lineElement = do
  let linePath = indexedPath (childPath "/Invoice" "InvoiceLine") index
  lineId <- optionalTextChild linePath cbcNamespace "ID" lineElement
  lineNote <- optionalTextChild linePath cbcNamespace "Note" lineElement
  quantity <- optionalQuantityChild linePath "InvoicedQuantity" lineElement
  lineExtensionAmount <- fmap amountValueDecimal <$> optionalAmountChild linePath "LineExtensionAmount" lineElement
  accountingCost <- optionalTextChild linePath cbcNamespace "AccountingCost" lineElement
  linePeriod <- optionalChildElement linePath cacNamespace "InvoicePeriod" lineElement >>= traverse (parseLinePeriod (childPath linePath "InvoicePeriod"))
  orderLineReference <- optionalChildElement linePath cacNamespace "OrderLineReference" lineElement
  orderLineReferenceId <- maybe (pure Nothing) (optionalTextChild (childPath linePath "OrderLineReference") cbcNamespace "LineID") orderLineReference
  lineObjectReference <- parseLineObjectReference linePath lineElement
  lineAllowanceCharges <- traverse (uncurry parseLineAllowanceChargeWrapper) $
    indexedElements (childElements cacNamespace "AllowanceCharge" lineElement) (childPath linePath "AllowanceCharge")
  let lineAllowances = [entry | (False, entry) <- lineAllowanceCharges]
      lineCharges = [entry | (True, entry) <- lineAllowanceCharges]
  itemElement <- optionalChildElement linePath cacNamespace "Item" lineElement
  (lineItem, lineVat) <-
    case itemElement of
      Nothing -> pure (Nothing, Nothing)
      Just item -> parseLineItemAndVat (childPath linePath "Item") item
  linePrice <- optionalChildElement linePath cacNamespace "Price" lineElement >>= traverse (parseLinePrice (childPath linePath "Price"))
  pure
    Line
      { lineId = lineId
      , lineNote = lineNote
      , lineObjectReference = lineObjectReference
      , lineQuantity = quantityValueDecimal =<< quantity
      , lineQuantityUnitCode = quantityValueUnitCode =<< quantity
      , lineLineExtensionAmount = joinMaybe lineExtensionAmount
      , lineAccountingCost = accountingCost
      , lineOrderLineReference = orderLineReferenceId
      , linePeriod = linePeriod
      , lineAllowances = lineAllowances
      , lineCharges = lineCharges
      , linePrice = linePrice
      , lineVat = lineVat
      , lineItem = lineItem
      }

parseLinePeriod :: Text -> Element -> Decode LinePeriod
parseLinePeriod periodPath periodElement = do
  startDate <- optionalDayChild periodPath "StartDate" periodElement
  endDate <- optionalDayChild periodPath "EndDate" periodElement
  pure
    LinePeriod
      { linePeriodStartDate = startDate
      , linePeriodEndDate = endDate
      }

parseLineObjectReference :: Text -> Element -> Decode (Maybe Reference)
parseLineObjectReference linePath lineElement = do
  documentReferences <- traverse (uncurry parseLineDocumentReference) $
    indexedElements (childElements cacNamespace "DocumentReference" lineElement) (childPath linePath "DocumentReference")
  case catMaybes documentReferences of
    [] -> pure Nothing
    [reference] -> pure (Just reference)
    _ -> decodeError (childPath linePath "DocumentReference") "Mehrere lineObjectReference-Eintraege mit DocumentTypeCode 130 werden nicht unterstuetzt."

parseLineDocumentReference :: Int -> Element -> Decode (Maybe Reference)
parseLineDocumentReference index referenceElement = do
  let referencePath = indexedPath (childPath "/Invoice/InvoiceLine" "DocumentReference") index
  documentTypeCode <- optionalTextChild referencePath cbcNamespace "DocumentTypeCode" referenceElement
  if documentTypeCode == Just "130"
    then do
      identifier <- optionalIdentifierChild referencePath "ID" referenceElement
      pure $
        Just
          Reference
            { referenceId = identifierValue =<< identifier
            , referenceSchemeId = identifierSchemeId =<< identifier
            }
    else pure Nothing

parseLineAllowanceChargeWrapper :: Int -> Element -> Decode (Bool, LineAllowanceCharge)
parseLineAllowanceChargeWrapper index allowanceElement = do
  let allowancePath = indexedPath (childPath "/Invoice/InvoiceLine" "AllowanceCharge") index
  chargeIndicator <- requiredBoolChild allowancePath "ChargeIndicator" allowanceElement
  reasonCode <- optionalTextChild allowancePath cbcNamespace "AllowanceChargeReasonCode" allowanceElement
  reasonText <- optionalTextChild allowancePath cbcNamespace "AllowanceChargeReason" allowanceElement
  percent <- optionalDecimalChild allowancePath "MultiplierFactorNumeric" allowanceElement
  amount <- fmap amountValueDecimal <$> optionalAmountChild allowancePath "Amount" allowanceElement
  baseAmount <- fmap amountValueDecimal <$> optionalAmountChild allowancePath "BaseAmount" allowanceElement
  pure
    ( chargeIndicator
    , LineAllowanceCharge
        { lineAllowanceChargeAmount = joinMaybe amount
        , lineAllowanceChargeBaseAmount = joinMaybe baseAmount
        , lineAllowanceChargePercent = percent
        , lineAllowanceChargeReason = reasonText
        , lineAllowanceChargeReasonCode = reasonCode
        }
    )

parseLinePrice :: Text -> Element -> Decode LinePrice
parseLinePrice pricePath priceElement = do
  priceAmount <- fmap amountValueDecimal <$> optionalAmountChild pricePath "PriceAmount" priceElement
  baseQuantity <- optionalQuantityChild pricePath "BaseQuantity" priceElement
  discounts <- traverse (uncurry parsePriceAllowanceCharge) $
    indexedElements (childElements cacNamespace "AllowanceCharge" priceElement) (childPath pricePath "AllowanceCharge")
  priceDiscount <- classifyPriceDiscounts pricePath discounts
  pure
    LinePrice
      { linePriceNetAmount = joinMaybe priceAmount
      , linePriceDiscount = priceDiscount
      , linePriceBaseQuantity = quantityValueDecimal =<< baseQuantity
      , linePriceBaseQuantityUnitCode = quantityValueUnitCode =<< baseQuantity
      }

parsePriceAllowanceCharge :: Int -> Element -> Decode PriceAllowanceCharge
parsePriceAllowanceCharge index allowanceElement = do
  let allowancePath = indexedPath (childPath "/Invoice/InvoiceLine/Price" "AllowanceCharge") index
  chargeIndicator <- requiredBoolChild allowancePath "ChargeIndicator" allowanceElement
  amount <- fmap amountValueDecimal <$> optionalAmountChild allowancePath "Amount" allowanceElement
  baseAmount <- fmap amountValueDecimal <$> optionalAmountChild allowancePath "BaseAmount" allowanceElement
  pure
    PriceAllowanceCharge
      { priceAllowanceChargeIsCharge = chargeIndicator
      , priceAllowanceChargeDiscount =
          PriceDiscount
            { priceDiscountAmount = joinMaybe amount
            , priceDiscountBaseAmount = joinMaybe baseAmount
            }
      }

classifyPriceDiscounts :: Text -> [PriceAllowanceCharge] -> Decode (Maybe PriceDiscount)
classifyPriceDiscounts pricePath allowances =
  case allowances of
    [] -> pure Nothing
    [allowance]
      | not (priceAllowanceChargeIsCharge allowance) ->
          pure (Just (priceAllowanceChargeDiscount allowance))
      | otherwise ->
          decodeError (childPath pricePath "AllowanceCharge") "Ein Preisnachlass darf keinen ChargeIndicator=true haben."
    _ ->
      decodeError (childPath pricePath "AllowanceCharge") "Mehrere Price/AllowanceCharge-Eintraege werden im Modell nicht abgebildet."

parseLineItemAndVat :: Text -> Element -> Decode (Maybe LineItem, Maybe LineVat)
parseLineItemAndVat itemPath itemElement = do
  description <- optionalTextChild itemPath cbcNamespace "Description" itemElement
  name <- optionalTextChild itemPath cbcNamespace "Name" itemElement
  buyersItemId <- optionalTextAtPath (childPath itemPath "BuyersItemIdentification") itemElement cacNamespace "BuyersItemIdentification" cbcNamespace "ID"
  sellersItemId <- optionalTextAtPath (childPath itemPath "SellersItemIdentification") itemElement cacNamespace "SellersItemIdentification" cbcNamespace "ID"
  standardItemIdentification <- optionalChildElement itemPath cacNamespace "StandardItemIdentification" itemElement
  standardIdentifier <- maybe (pure Nothing) (optionalIdentifierChild (childPath itemPath "StandardItemIdentification") "ID") standardItemIdentification
  originCountry <- optionalTextAtPath (childPath itemPath "OriginCountry") itemElement cacNamespace "OriginCountry" cbcNamespace "IdentificationCode"
  classifications <- traverse (uncurry parseClassification) $
    indexedElements (childElements cacNamespace "CommodityClassification" itemElement) (childPath itemPath "CommodityClassification")
  taxCategory <- optionalChildElement itemPath cacNamespace "ClassifiedTaxCategory" itemElement
  vat <- traverse (parseLineVat (childPath itemPath "ClassifiedTaxCategory")) taxCategory
  attributes <- traverse (uncurry parseItemAttribute) $
    indexedElements (childElements cacNamespace "AdditionalItemProperty" itemElement) (childPath itemPath "AdditionalItemProperty")
  let lineItem =
        if hasRelevantLineItemData description name buyersItemId sellersItemId standardIdentifier originCountry classifications attributes
          then
            Just
              LineItem
                { lineItemName = name
                , lineItemDescription = description
                , lineItemSellersItemId = sellersItemId
                , lineItemBuyersItemId = buyersItemId
                , lineItemStandardId = identifierValue =<< standardIdentifier
                , lineItemStandardIdSchemeId = identifierSchemeId =<< standardIdentifier
                , lineItemClassifications = classifications
                , lineItemOriginCountryCode = originCountry
                , lineItemAttributes = attributes
                }
          else Nothing
  pure (lineItem, vat)

parseClassification :: Int -> Element -> Decode Classification
parseClassification index classificationElement = do
  let classificationPath = indexedPath (childPath "/Invoice/InvoiceLine/Item" "CommodityClassification") index
  codeElement <- optionalChildElement classificationPath cbcNamespace "ItemClassificationCode" classificationElement
  pure
    Classification
      { classificationCode = codeElement >>= elementText
      , classificationListId = codeElement >>= lookupAttr "listID"
      , classificationListVersionId = codeElement >>= lookupAttr "listVersionID"
      }

parseLineVat :: Text -> Element -> Decode LineVat
parseLineVat vatPath vatElement = do
  (categoryCode, rate, _, _) <- parseTaxCategoryFields vatPath vatElement
  pure
    LineVat
      { lineVatCategoryCode = categoryCode
      , lineVatRate = rate
      }

parseItemAttribute :: Int -> Element -> Decode ItemAttribute
parseItemAttribute index attributeElement = do
  let attributePath = indexedPath (childPath "/Invoice/InvoiceLine/Item" "AdditionalItemProperty") index
  attributeName <- optionalTextChild attributePath cbcNamespace "Name" attributeElement
  attributeValue <- optionalTextChild attributePath cbcNamespace "Value" attributeElement
  pure
    ItemAttribute
      { itemAttributeName = attributeName
      , itemAttributeValue = attributeValue
      }

parseAttachment :: Text -> Element -> Decode Attachment
parseAttachment attachmentPath attachmentElement = do
  embedded <- optionalChildElement attachmentPath cbcNamespace "EmbeddedDocumentBinaryObject" attachmentElement
  externalReference <- optionalChildElement attachmentPath cacNamespace "ExternalReference" attachmentElement
  externalUri <- maybe (pure Nothing) (optionalTextChild (childPath attachmentPath "ExternalReference") cbcNamespace "URI") externalReference
  pure
    Attachment
      { attachmentExternalUri = externalUri
      , attachmentContent = embedded >>= elementText
      , attachmentMimeCode = embedded >>= lookupAttr "mimeCode"
      , attachmentFilename = embedded >>= lookupAttr "filename"
      }

parseAddress :: Text -> Element -> Decode Address
parseAddress addressPath addressElement = do
  street <- optionalTextChild addressPath cbcNamespace "StreetName" addressElement
  additionalStreet <- optionalTextChild addressPath cbcNamespace "AdditionalStreetName" addressElement
  city <- optionalTextChild addressPath cbcNamespace "CityName" addressElement
  postalCode <- optionalTextChild addressPath cbcNamespace "PostalZone" addressElement
  countrySubdivision <- optionalTextChild addressPath cbcNamespace "CountrySubentity" addressElement
  addressLineWrapper <- optionalChildElement addressPath cacNamespace "AddressLine" addressElement
  addressLine <- maybe (pure Nothing) (optionalTextChild (childPath addressPath "AddressLine") cbcNamespace "Line") addressLineWrapper
  countryWrapper <- optionalChildElement addressPath cacNamespace "Country" addressElement
  countryCode <- maybe (pure Nothing) (optionalTextChild (childPath addressPath "Country") cbcNamespace "IdentificationCode") countryWrapper
  pure
    Address
      { addressStreet = street
      , addressAdditionalStreet = additionalStreet
      , addressAddressLine = addressLine
      , addressCity = city
      , addressPostalCode = postalCode
      , addressCountrySubdivision = countrySubdivision
      , addressCountryCode = countryCode
      }

parseContact :: Text -> Element -> Decode Contact
parseContact contactPath contactElement = do
  contactName <- optionalTextChild contactPath cbcNamespace "Name" contactElement
  phone <- optionalTextChild contactPath cbcNamespace "Telephone" contactElement
  email <- optionalTextChild contactPath cbcNamespace "ElectronicMail" contactElement
  pure
    Contact
      { contactName = contactName
      , contactPhone = phone
      , contactEmail = email
      }

parsePartyName :: Text -> Maybe Element -> Decode (Maybe Text)
parsePartyName _ Nothing = pure Nothing
parsePartyName parentPath (Just parentElement) = do
  wrapper <- optionalChildElement parentPath cacNamespace "PartyName" parentElement
  maybe (pure Nothing) (optionalTextChild (childPath parentPath "PartyName") cbcNamespace "Name") wrapper

parsePartyIdentification :: Int -> Element -> Decode Identifier
parsePartyIdentification index identificationElement = do
  let identificationPath = indexedPath "/Invoice/PartyIdentification" index
  identifier <- optionalIdentifierChild identificationPath "ID" identificationElement
  pure $ fromMaybe emptyIdentifier identifier

parsePartyTaxScheme :: Int -> Element -> Decode PartyTaxSchemeInfo
parsePartyTaxScheme index taxSchemeElement = do
  let taxSchemePath = indexedPath "/Invoice/PartyTaxScheme" index
  companyId <- optionalTextChild taxSchemePath cbcNamespace "CompanyID" taxSchemeElement
  taxScheme <- optionalChildElement taxSchemePath cacNamespace "TaxScheme" taxSchemeElement
  schemeCode <- maybe (pure Nothing) (optionalTextChild (childPath taxSchemePath "TaxScheme") cbcNamespace "ID") taxScheme
  pure
    PartyTaxSchemeInfo
      { partyTaxSchemeCode = schemeCode
      , partyTaxSchemeCompanyId = companyId
      }

parseTax :: Text -> Element -> Decode Tax
parseTax taxPath taxElement = do
  (categoryCode, rate, exemptionReason, exemptionReasonCode) <- parseTaxCategoryFields taxPath taxElement
  pure
    Tax
      { taxCategoryCode = categoryCode
      , taxRate = rate
      , taxExemptionReason = exemptionReason
      , taxExemptionReasonCode = exemptionReasonCode
      }

parseTaxCategoryFields :: Text -> Element -> Decode (Maybe Text, Maybe Decimal, Maybe Text, Maybe Text)
parseTaxCategoryFields categoryPath categoryElement = do
  categoryCode <- optionalTextChild categoryPath cbcNamespace "ID" categoryElement
  rate <- optionalDecimalChild categoryPath "Percent" categoryElement
  exemptionReason <- optionalTextChild categoryPath cbcNamespace "TaxExemptionReason" categoryElement
  exemptionReasonCode <- optionalTextChild categoryPath cbcNamespace "TaxExemptionReasonCode" categoryElement
  pure (categoryCode, rate, exemptionReason, exemptionReasonCode)

optionalTextAtPath :: Text -> Element -> Text -> Text -> Text -> Text -> Decode (Maybe Text)
optionalTextAtPath wrapperPath parentElement wrapperNamespace wrapperName childNamespace childName = do
  wrapper <- optionalChildElement wrapperPath wrapperNamespace wrapperName parentElement
  maybe (pure Nothing) (optionalTextChild wrapperPath childNamespace childName) wrapper

optionalChildElementMaybe :: Text -> Text -> Text -> Maybe Element -> Decode (Maybe Element)
optionalChildElementMaybe _ _ _ Nothing = pure Nothing
optionalChildElementMaybe parentPath namespace localName (Just parentElement) =
  optionalChildElement parentPath namespace localName parentElement

optionalIdentifierChildMaybe :: Text -> Text -> Maybe Element -> Decode (Maybe Identifier)
optionalIdentifierChildMaybe _ _ Nothing = pure Nothing
optionalIdentifierChildMaybe parentPath localName (Just parentElement) =
  optionalIdentifierChild parentPath localName parentElement

optionalChildElement :: Text -> Text -> Text -> Element -> Decode (Maybe Element)
optionalChildElement parentPath namespace localName parentElement =
  uniqueElement (childPath parentPath localName) (childElements namespace localName parentElement)

optionalTextChild :: Text -> Text -> Text -> Element -> Decode (Maybe Text)
optionalTextChild parentPath namespace localName parentElement = do
  child <- optionalChildElement parentPath namespace localName parentElement
  pure (child >>= elementText)

optionalIdentifierChild :: Text -> Text -> Element -> Decode (Maybe Identifier)
optionalIdentifierChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  pure $ fmap parseIdentifier child

optionalAmountChild :: Text -> Text -> Element -> Decode (Maybe AmountValue)
optionalAmountChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  traverse (parseAmountValue (childPath parentPath localName)) child

optionalQuantityChild :: Text -> Text -> Element -> Decode (Maybe QuantityValue)
optionalQuantityChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  traverse (parseQuantityValue (childPath parentPath localName)) child

optionalDecimalChild :: Text -> Text -> Element -> Decode (Maybe Decimal)
optionalDecimalChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  case child >>= elementText of
    Nothing -> pure Nothing
    Just value -> Just <$> parseDecimalAt (childPath parentPath localName) value

optionalDayChild :: Text -> Text -> Element -> Decode (Maybe Day)
optionalDayChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  case child >>= elementText of
    Nothing -> pure Nothing
    Just value -> Just <$> parseDayAt (childPath parentPath localName) value

requiredBoolChild :: Text -> Text -> Element -> Decode Bool
requiredBoolChild parentPath localName parentElement = do
  child <- optionalChildElement parentPath cbcNamespace localName parentElement
  case child >>= elementText of
    Nothing -> decodeError (childPath parentPath localName) "Ein Bool-Wert wird erwartet."
    Just value -> parseBoolAt (childPath parentPath localName) value

parseAmountValue :: Text -> Element -> Decode AmountValue
parseAmountValue amountPath amountElement = do
  amount <- case elementText amountElement of
    Nothing -> pure Nothing
    Just value -> Just <$> parseDecimalAt amountPath value
  pure
    AmountValue
      { amountValueDecimal = amount
      , amountValueCurrency = lookupAttr "currencyID" amountElement
      }

parseQuantityValue :: Text -> Element -> Decode QuantityValue
parseQuantityValue quantityPath quantityElement = do
  quantity <- case elementText quantityElement of
    Nothing -> pure Nothing
    Just value -> Just <$> parseDecimalAt quantityPath value
  pure
    QuantityValue
      { quantityValueDecimal = quantity
      , quantityValueUnitCode = lookupAttr "unitCode" quantityElement
      }

parseIdentifier :: Element -> Identifier
parseIdentifier identifierElement =
  Identifier
    { identifierValue = elementText identifierElement
    , identifierSchemeId = lookupAttr "schemeID" identifierElement
    }

identifierToReference :: Identifier -> Reference
identifierToReference identifier =
  Reference
    { referenceId = identifierValue identifier
    , referenceSchemeId = identifierSchemeId identifier
    }

parseDecimalAt :: Text -> Text -> Decode Decimal
parseDecimalAt valuePath rawValue =
  case rational rawValue of
    Left _ -> decodeError valuePath ("Ungueltige Dezimalzahl: " <> rawValue)
    Right (value, remainder)
      | T.null remainder -> pure (Decimal value)
      | otherwise -> decodeError valuePath ("Ungueltige Dezimalzahl: " <> rawValue)

parseDayAt :: Text -> Text -> Decode Day
parseDayAt valuePath rawValue =
  case parseTimeM True defaultTimeLocale "%F" (T.unpack rawValue) of
    Nothing -> decodeError valuePath ("Ungueltiges Datum: " <> rawValue)
    Just day -> pure day

parseBoolAt :: Text -> Text -> Decode Bool
parseBoolAt valuePath rawValue =
  case T.toCaseFold rawValue of
    "true" -> pure True
    "1" -> pure True
    "false" -> pure False
    "0" -> pure False
    _ -> decodeError valuePath ("Ungueltiger Bool-Wert: " <> rawValue)

uniqueElement :: Text -> [Element] -> Decode (Maybe Element)
uniqueElement _ [] = pure Nothing
uniqueElement _ [element] = pure (Just element)
uniqueElement elementPath _ = decodeError elementPath "Element darf hoechstens einmal vorkommen."

uniqueIdentifier :: Text -> [Identifier] -> Decode (Maybe Identifier)
uniqueIdentifier _ [] = pure Nothing
uniqueIdentifier _ [identifier] = pure (Just identifier)
uniqueIdentifier elementPath _ = decodeError elementPath "Mehrere Identifier koennen im Modell nicht abgebildet werden."

uniquePartyTaxCompanyId :: Text -> Text -> [PartyTaxSchemeInfo] -> Decode (Maybe Text)
uniquePartyTaxCompanyId taxSchemePath expectedSchemeCode partyTaxSchemes =
  case [partyTaxSchemeCompanyId info | info <- partyTaxSchemes, partyTaxSchemeCode info == Just expectedSchemeCode] of
    [] -> pure Nothing
    [companyId] -> pure companyId
    _ -> decodeError taxSchemePath ("Mehrere PartyTaxScheme-Eintraege fuer " <> expectedSchemeCode <> " werden nicht unterstuetzt.")

extractSepaCreditorId :: Text -> [Identifier] -> Decode (Maybe Text)
extractSepaCreditorId identifierPath identifiers =
  case [identifierValue identifier | identifier <- identifiers, isSepaIdentifier identifier] of
    [] -> pure Nothing
    [value] -> pure value
    _ -> decodeError identifierPath "Mehrere SEPA-Creditor-IDs koennen im Modell nicht abgebildet werden."

isSepaIdentifier :: Identifier -> Bool
isSepaIdentifier identifier =
  fmap T.toCaseFold (identifierSchemeId identifier) == Just "sepa"

hasRelevantLineItemData ::
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Identifier ->
  Maybe Text ->
  [Classification] ->
  [ItemAttribute] ->
  Bool
hasRelevantLineItemData description name buyersItemId sellersItemId standardIdentifier originCountry classifications attributes =
  or
    [ isJust description
    , isJust name
    , isJust buyersItemId
    , isJust sellersItemId
    , isJust standardIdentifier
    , isJust originCountry
    , not (null classifications)
    , not (null attributes)
    ]

matchesName :: Text -> Text -> Element -> Bool
matchesName namespace localName element =
  nameNamespace (elementName element) == Just namespace
    && nameLocalName (elementName element) == localName

childElements :: Text -> Text -> Element -> [Element]
childElements namespace localName =
  mapMaybe nodeElement . filter (isNamedNode namespace localName) . elementNodes

childElementsMaybe :: Text -> Text -> Maybe Element -> [Element]
childElementsMaybe _ _ Nothing = []
childElementsMaybe namespace localName (Just element) = childElements namespace localName element

isNamedNode :: Text -> Text -> Node -> Bool
isNamedNode namespace localName (NodeElement element) = matchesName namespace localName element
isNamedNode _ _ _ = False

nodeElement :: Node -> Maybe Element
nodeElement (NodeElement element) = Just element
nodeElement _ = Nothing

elementText :: Element -> Maybe Text
elementText element = normalizeText (T.concat [content | NodeContent content <- elementNodes element])

lookupAttr :: Text -> Element -> Maybe Text
lookupAttr key element =
  Map.lookup (Name key Nothing Nothing) (elementAttributes element) >>= normalizeText

normalizeText :: Text -> Maybe Text
normalizeText rawText =
  let normalized = T.strip rawText
   in if T.null normalized then Nothing else Just normalized

splitSubjectCode :: Text -> Maybe (Text, Text)
splitSubjectCode rawText =
  case T.uncons rawText of
    Just ('#', remainder) ->
      let (subjectCode, rest) = T.breakOn "#" remainder
       in if T.null subjectCode || T.null rest
            then Nothing
            else case normalizeText (T.drop 1 rest) of
              Nothing -> Nothing
              Just body -> Just (subjectCode, body)
    _ -> Nothing

indexedElements :: [Element] -> Text -> [(Int, Element)]
indexedElements elements basePath =
  zip [1 :: Int ..] elements
    & map (\(index, element) -> (index, element))
  where
    (&) = flip ($)

childPath :: Text -> Text -> Text
childPath parentPath localName = parentPath <> "/" <> localName

indexedPath :: Text -> Int -> Text
indexedPath basePath index = basePath <> "[" <> T.pack (show index) <> "]"

joinMaybe :: Maybe (Maybe a) -> Maybe a
joinMaybe = fromMaybe Nothing

decodeError :: Text -> Text -> Decode a
decodeError errorPath message =
  Left
    XmlDecodeError
      { xmlDecodeErrorPath = errorPath
      , xmlDecodeErrorDescription = message
      }

emptyIdentifier :: Identifier
emptyIdentifier =
  Identifier
    { identifierValue = Nothing
    , identifierSchemeId = Nothing
    }

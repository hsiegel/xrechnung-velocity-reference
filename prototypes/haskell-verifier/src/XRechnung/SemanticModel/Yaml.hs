{-# LANGUAGE OverloadedStrings #-}

module XRechnung.SemanticModel.Yaml
  ( YamlDecodeError (..)
  , renderYamlDecodeError
  , parseSemanticModelYaml
  , semanticModelYamlToXml
  ) where

import Data.Aeson.Key (fromText)
import Data.Aeson.Types (Parser, parseEither)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Text.Read (rational)
import Data.Time (Day)
import Data.Time.Format (defaultTimeLocale, parseTimeM)
import Data.Yaml
  ( Object
  , ParseException
  , Value
  , decodeEither'
  , prettyPrintParseException
  , withObject
  , withScientific
  , withText
  , (.:)
  , (.:?)
  )
import XRechnung.Model
import XRechnung.Ubl.Render (renderUblXml)

data YamlDecodeError = YamlDecodeError
  { yamlDecodeErrorDescription :: Text
  }
  deriving (Eq, Show)

renderYamlDecodeError :: YamlDecodeError -> Text
renderYamlDecodeError = yamlDecodeErrorDescription

parseSemanticModelYaml :: Text -> Either YamlDecodeError XRechnung
parseSemanticModelYaml yamlText =
  case decodeEither' (TE.encodeUtf8 yamlText) of
    Left parseException -> Left (yamlParseException parseException)
    Right value ->
      case parseEither parseSemanticModelRoot value of
        Left parseError -> Left (YamlDecodeError (T.pack parseError))
        Right invoice -> Right invoice

semanticModelYamlToXml :: Text -> Either YamlDecodeError Text
semanticModelYamlToXml = fmap renderUblXml . parseSemanticModelYaml

parseSemanticModelRoot :: Value -> Parser XRechnung
parseSemanticModelRoot =
  withObject "semantic-model root" $ \root -> do
    xrValue <- root .: fromText "xr"
    withObject "xr" parseXRechnung xrValue

parseXRechnung :: Object -> Parser XRechnung
parseXRechnung object =
  XRechnung
    <$> optionalObjectField object "process" parseProcess
    <*> optionalObjectField object "invoice" parseInvoice
    <*> optionalObjectListField object "notes" parseNote
    <*> optionalObjectField object "invoicePeriod" parseInvoicePeriod
    <*> optionalTextField object "projectReferenceId"
    <*> optionalTextField object "contractReferenceId"
    <*> optionalTextField object "receiptReferenceId"
    <*> optionalTextField object "despatchReferenceId"
    <*> optionalTextField object "originatorReferenceId"
    <*> optionalObjectField object "orderReference" parseOrderReference
    <*> optionalObjectListField object "precedingInvoices" parsePrecedingInvoice
    <*> optionalObjectField object "invoiceObjectReference" parseReference
    <*> optionalObjectField object "seller" parseSeller
    <*> optionalObjectField object "buyer" parseBuyer
    <*> optionalObjectField object "payee" parsePayee
    <*> optionalObjectField object "taxRepresentative" parseTaxRepresentative
    <*> optionalObjectField object "delivery" parseDelivery
    <*> optionalObjectField object "payment" parsePayment
    <*> optionalObjectField object "paymentTerms" parsePaymentTerms
    <*> optionalObjectListField object "documentAllowances" parseDocumentAllowanceCharge
    <*> optionalObjectListField object "documentCharges" parseDocumentAllowanceCharge
    <*> optionalObjectField object "totals" parseTotals
    <*> optionalObjectListField object "vatBreakdowns" parseVatBreakdown
    <*> optionalObjectListField object "supportingDocuments" parseSupportingDocument
    <*> optionalObjectListField object "lines" parseLine

parseProcess :: Object -> Parser Process
parseProcess object =
  Process
    <$> optionalTextField object "customizationId"
    <*> optionalTextField object "profileId"

parseInvoice :: Object -> Parser Invoice
parseInvoice object =
  Invoice
    <$> optionalTextField object "id"
    <*> optionalDayField object "issueDate"
    <*> optionalDayField object "dueDate"
    <*> optionalTextField object "typeCode"
    <*> optionalTextField object "documentCurrencyCode"
    <*> optionalTextField object "taxCurrencyCode"
    <*> optionalDayField object "taxPointDate"
    <*> optionalTextField object "buyerReference"
    <*> optionalTextField object "accountingCost"

parseNote :: Object -> Parser Note
parseNote object =
  Note
    <$> optionalTextField object "subjectCode"
    <*> optionalTextField object "text"

parseInvoicePeriod :: Object -> Parser InvoicePeriod
parseInvoicePeriod object =
  InvoicePeriod
    <$> optionalTextField object "descriptionCode"
    <*> optionalDayField object "startDate"
    <*> optionalDayField object "endDate"

parseOrderReference :: Object -> Parser OrderReference
parseOrderReference object =
  OrderReference
    <$> optionalTextField object "id"
    <*> optionalTextField object "salesOrderId"

parsePrecedingInvoice :: Object -> Parser PrecedingInvoice
parsePrecedingInvoice object =
  PrecedingInvoice
    <$> optionalTextField object "id"
    <*> optionalDayField object "issueDate"

parseIdentifier :: Object -> Parser Identifier
parseIdentifier object =
  Identifier
    <$> optionalTextField object "value"
    <*> optionalTextField object "schemeId"

parseReference :: Object -> Parser Reference
parseReference object =
  Reference
    <$> optionalTextField object "id"
    <*> optionalTextField object "schemeId"

parseAddress :: Object -> Parser Address
parseAddress object =
  Address
    <$> optionalTextField object "street"
    <*> optionalTextField object "additionalStreet"
    <*> optionalTextField object "addressLine"
    <*> optionalTextField object "city"
    <*> optionalTextField object "postalCode"
    <*> optionalTextField object "countrySubdivision"
    <*> optionalTextField object "countryCode"

parseContact :: Object -> Parser Contact
parseContact object =
  Contact
    <$> optionalTextField object "name"
    <*> optionalTextField object "phone"
    <*> optionalTextField object "email"

parseTax :: Object -> Parser Tax
parseTax object =
  Tax
    <$> optionalTextField object "categoryCode"
    <*> optionalDecimalField object "rate"
    <*> optionalTextField object "exemptionReason"
    <*> optionalTextField object "exemptionReasonCode"

parseAttachment :: Object -> Parser Attachment
parseAttachment object =
  Attachment
    <$> optionalTextField object "externalUri"
    <*> optionalTextField object "content"
    <*> optionalTextField object "mimeCode"
    <*> optionalTextField object "filename"

parseClassification :: Object -> Parser Classification
parseClassification object =
  Classification
    <$> optionalTextField object "code"
    <*> optionalTextField object "listId"
    <*> optionalTextField object "listVersionId"

parseItemAttribute :: Object -> Parser ItemAttribute
parseItemAttribute object =
  ItemAttribute
    <$> optionalTextField object "name"
    <*> optionalTextField object "value"

parseSeller :: Object -> Parser Seller
parseSeller object =
  Seller
    <$> optionalTextField object "name"
    <*> optionalTextField object "tradeName"
    <*> optionalObjectField object "endpoint" parseIdentifier
    <*> optionalObjectListField object "identifiers" parseIdentifier
    <*> optionalTextField object "sepaCreditorId"
    <*> optionalTextField object "vatIdentifier"
    <*> optionalTextField object "taxIdentifier"
    <*> optionalTextField object "legalRegistrationId"
    <*> optionalTextField object "legalRegistrationIdSchemeId"
    <*> optionalTextField object "legalForm"
    <*> optionalObjectField object "address" parseAddress
    <*> optionalObjectField object "contact" parseContact

parseBuyer :: Object -> Parser Buyer
parseBuyer object =
  Buyer
    <$> optionalTextField object "name"
    <*> optionalTextField object "tradeName"
    <*> optionalObjectField object "endpoint" parseIdentifier
    <*> optionalObjectField object "identifier" parseIdentifier
    <*> optionalTextField object "vatIdentifier"
    <*> optionalTextField object "legalRegistrationId"
    <*> optionalTextField object "legalRegistrationIdSchemeId"
    <*> optionalObjectField object "address" parseAddress
    <*> optionalObjectField object "contact" parseContact

parsePayee :: Object -> Parser Payee
parsePayee object =
  Payee
    <$> optionalTextField object "name"
    <*> optionalObjectField object "identifier" parseIdentifier
    <*> optionalTextField object "sepaCreditorId"
    <*> optionalTextField object "legalRegistrationId"
    <*> optionalTextField object "legalRegistrationIdSchemeId"

parseTaxRepresentative :: Object -> Parser TaxRepresentative
parseTaxRepresentative object =
  TaxRepresentative
    <$> optionalTextField object "name"
    <*> optionalTextField object "vatIdentifier"
    <*> optionalObjectField object "address" parseAddress

parseDelivery :: Object -> Parser Delivery
parseDelivery object =
  Delivery
    <$> optionalTextField object "partyName"
    <*> optionalDayField object "actualDate"
    <*> optionalObjectField object "location" parseReference
    <*> optionalObjectField object "address" parseAddress

parsePayment :: Object -> Parser Payment
parsePayment object =
  Payment
    <$> optionalTextField object "meansCode"
    <*> optionalTextField object "meansText"
    <*> optionalTextField object "paymentId"
    <*> optionalObjectListField object "payeeAccounts" parsePayeeAccount
    <*> optionalObjectField object "card" parsePaymentCard
    <*> optionalObjectField object "mandate" parsePaymentMandate

parsePayeeAccount :: Object -> Parser PayeeAccount
parsePayeeAccount object =
  PayeeAccount
    <$> optionalTextField object "id"
    <*> optionalTextField object "name"
    <*> optionalTextField object "bic"

parsePaymentCard :: Object -> Parser PaymentCard
parsePaymentCard object =
  PaymentCard
    <$> optionalTextField object "primaryAccountNumberId"
    <*> optionalTextField object "holderName"

parsePaymentMandate :: Object -> Parser PaymentMandate
parsePaymentMandate object =
  PaymentMandate
    <$> optionalTextField object "id"
    <*> optionalTextField object "payerAccountId"

parsePaymentTerms :: Object -> Parser PaymentTerms
parsePaymentTerms object =
  PaymentTerms
    <$> optionalTextField object "note"

parseDocumentAllowanceCharge :: Object -> Parser DocumentAllowanceCharge
parseDocumentAllowanceCharge object =
  DocumentAllowanceCharge
    <$> optionalDecimalField object "amount"
    <*> optionalDecimalField object "baseAmount"
    <*> optionalDecimalField object "percent"
    <*> optionalTextField object "reason"
    <*> optionalTextField object "reasonCode"
    <*> optionalObjectField object "tax" parseTax

parseTotals :: Object -> Parser Totals
parseTotals object =
  Totals
    <$> optionalDecimalField object "lineExtensionAmount"
    <*> optionalDecimalField object "allowanceTotalAmount"
    <*> optionalDecimalField object "chargeTotalAmount"
    <*> optionalDecimalField object "taxExclusiveAmount"
    <*> optionalDecimalField object "taxAmountInDocumentCurrency"
    <*> optionalDecimalField object "taxAmountInTaxCurrency"
    <*> optionalDecimalField object "taxInclusiveAmount"
    <*> optionalDecimalField object "prepaidAmount"
    <*> optionalDecimalField object "payableRoundingAmount"
    <*> optionalDecimalField object "payableAmount"

parseVatBreakdown :: Object -> Parser VatBreakdown
parseVatBreakdown object =
  VatBreakdown
    <$> optionalDecimalField object "taxableAmount"
    <*> optionalDecimalField object "taxAmount"
    <*> optionalTextField object "categoryCode"
    <*> optionalDecimalField object "rate"
    <*> optionalTextField object "exemptionReason"
    <*> optionalTextField object "exemptionReasonCode"

parseSupportingDocument :: Object -> Parser SupportingDocument
parseSupportingDocument object =
  SupportingDocument
    <$> optionalTextField object "id"
    <*> optionalTextField object "description"
    <*> optionalObjectField object "embedded" parseAttachment

parseLine :: Object -> Parser Line
parseLine object =
  Line
    <$> optionalTextField object "id"
    <*> optionalTextField object "note"
    <*> optionalObjectField object "objectReference" parseReference
    <*> optionalDecimalField object "quantity"
    <*> optionalTextField object "quantityUnitCode"
    <*> optionalDecimalField object "lineExtensionAmount"
    <*> optionalTextField object "accountingCost"
    <*> optionalTextField object "orderLineReference"
    <*> optionalObjectField object "period" parseLinePeriod
    <*> optionalObjectListField object "allowances" parseLineAllowanceCharge
    <*> optionalObjectListField object "charges" parseLineAllowanceCharge
    <*> optionalObjectField object "price" parseLinePrice
    <*> optionalObjectField object "vat" parseLineVat
    <*> optionalObjectField object "item" parseLineItem

parseLinePeriod :: Object -> Parser LinePeriod
parseLinePeriod object =
  LinePeriod
    <$> optionalDayField object "startDate"
    <*> optionalDayField object "endDate"

parseLineAllowanceCharge :: Object -> Parser LineAllowanceCharge
parseLineAllowanceCharge object =
  LineAllowanceCharge
    <$> optionalDecimalField object "amount"
    <*> optionalDecimalField object "baseAmount"
    <*> optionalDecimalField object "percent"
    <*> optionalTextField object "reason"
    <*> optionalTextField object "reasonCode"

parseLinePrice :: Object -> Parser LinePrice
parseLinePrice object =
  LinePrice
    <$> optionalDecimalField object "netAmount"
    <*> optionalObjectField object "discount" parsePriceDiscount
    <*> optionalDecimalField object "baseQuantity"
    <*> optionalTextField object "baseQuantityUnitCode"

parsePriceDiscount :: Object -> Parser PriceDiscount
parsePriceDiscount object =
  PriceDiscount
    <$> optionalDecimalField object "amount"
    <*> optionalDecimalField object "baseAmount"

parseLineVat :: Object -> Parser LineVat
parseLineVat object =
  LineVat
    <$> optionalTextField object "categoryCode"
    <*> optionalDecimalField object "rate"

parseLineItem :: Object -> Parser LineItem
parseLineItem object =
  LineItem
    <$> optionalTextField object "name"
    <*> optionalTextField object "description"
    <*> optionalTextField object "sellersItemId"
    <*> optionalTextField object "buyersItemId"
    <*> optionalTextField object "standardId"
    <*> optionalTextField object "standardIdSchemeId"
    <*> optionalObjectListField object "classifications" parseClassification
    <*> optionalTextField object "originCountryCode"
    <*> optionalObjectListField object "attributes" parseItemAttribute

optionalTextField :: Object -> Text -> Parser (Maybe Text)
optionalTextField object fieldName =
  object .:? fromText fieldName

optionalDayField :: Object -> Text -> Parser (Maybe Day)
optionalDayField object fieldName =
  optionalValueField object fieldName parseDayValue

optionalDecimalField :: Object -> Text -> Parser (Maybe Decimal)
optionalDecimalField object fieldName =
  optionalValueField object fieldName parseDecimalValue

optionalObjectField :: Object -> Text -> (Object -> Parser a) -> Parser (Maybe a)
optionalObjectField object fieldName parseObject = do
  maybeValue <- object .:? fromText fieldName :: Parser (Maybe Value)
  traverse (withObject (T.unpack fieldName) parseObject) maybeValue

optionalObjectListField :: Object -> Text -> (Object -> Parser a) -> Parser [a]
optionalObjectListField object fieldName parseObject = do
  maybeValues <- object .:? fromText fieldName :: Parser (Maybe [Value])
  case maybeValues of
    Nothing -> pure []
    Just values ->
      traverse (withObject (T.unpack fieldName) parseObject) values

optionalValueField :: Object -> Text -> (Value -> Parser a) -> Parser (Maybe a)
optionalValueField object fieldName parseValue = do
  maybeValue <- object .:? fromText fieldName :: Parser (Maybe Value)
  traverse parseValue maybeValue

parseDayValue :: Value -> Parser Day
parseDayValue =
  withText "date" $ \rawValue ->
    case parseTimeM True defaultTimeLocale "%F" (T.unpack rawValue) of
      Nothing -> fail ("Invalid date: " <> T.unpack rawValue)
      Just day -> pure day

parseDecimalValue :: Value -> Parser Decimal
parseDecimalValue value =
  case parseEither (withScientific "decimal" (pure . Decimal . toRational)) value of
    Right decimal -> pure decimal
    Left _ -> withText "decimal" parseTextDecimal value

parseTextDecimal :: Text -> Parser Decimal
parseTextDecimal rawValue =
  case rational rawValue of
    Left _ -> fail ("Invalid decimal: " <> T.unpack rawValue)
    Right (parsedValue, remainder)
      | T.null remainder -> pure (Decimal parsedValue)
      | otherwise -> fail ("Invalid decimal: " <> T.unpack rawValue)

yamlParseException :: ParseException -> YamlDecodeError
yamlParseException =
  YamlDecodeError
    . T.pack
    . prettyPrintParseException

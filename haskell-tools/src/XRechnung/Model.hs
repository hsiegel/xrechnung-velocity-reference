{-# LANGUAGE DeriveGeneric #-}

module XRechnung.Model where

import Data.Text (Text)
import Data.Time (Day)
import GHC.Generics (Generic)

-- XRechnung 3.0.2 / UBL Invoice / semantic full model

newtype Decimal = Decimal Rational
  deriving (Eq, Show, Generic)

data XRechnung = XRechnung
  { xrechnungProcess :: Maybe Process
  , xrechnungInvoice :: Maybe Invoice
  , xrechnungNotes :: [Note]
  , xrechnungInvoicePeriod :: Maybe InvoicePeriod
  , xrechnungProjectReferenceId :: Maybe Text
  , xrechnungContractReferenceId :: Maybe Text
  , xrechnungReceiptReferenceId :: Maybe Text
  , xrechnungDespatchReferenceId :: Maybe Text
  , xrechnungOriginatorReferenceId :: Maybe Text
  , xrechnungOrderReference :: Maybe OrderReference
  , xrechnungPrecedingInvoices :: [PrecedingInvoice]
  , xrechnungInvoiceObjectReference :: Maybe Reference
  , xrechnungSeller :: Maybe Seller
  , xrechnungBuyer :: Maybe Buyer
  , xrechnungPayee :: Maybe Payee
  , xrechnungTaxRepresentative :: Maybe TaxRepresentative
  , xrechnungDelivery :: Maybe Delivery
  , xrechnungPayment :: Maybe Payment
  , xrechnungPaymentTerms :: Maybe PaymentTerms
  , xrechnungDocumentAllowances :: [DocumentAllowanceCharge]
  , xrechnungDocumentCharges :: [DocumentAllowanceCharge]
  , xrechnungTotals :: Maybe Totals
  , xrechnungVatBreakdowns :: [VatBreakdown]
  , xrechnungSupportingDocuments :: [SupportingDocument]
  , xrechnungLines :: [Line]
  }
  deriving (Eq, Show, Generic)

data Process = Process
  { processCustomizationId :: Maybe Text
  , processProfileId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Invoice = Invoice
  { invoiceId :: Maybe Text
  , invoiceIssueDate :: Maybe Day
  , invoiceDueDate :: Maybe Day
  , invoiceTypeCode :: Maybe Text
  , invoiceDocumentCurrencyCode :: Maybe Text
  , invoiceTaxCurrencyCode :: Maybe Text
  , invoiceTaxPointDate :: Maybe Day
  , invoiceBuyerReference :: Maybe Text
  , invoiceAccountingCost :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Note = Note
  { noteSubjectCode :: Maybe Text
  , noteText :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data InvoicePeriod = InvoicePeriod
  { invoicePeriodDescriptionCode :: Maybe Text
  , invoicePeriodStartDate :: Maybe Day
  , invoicePeriodEndDate :: Maybe Day
  }
  deriving (Eq, Show, Generic)

data OrderReference = OrderReference
  { orderReferenceId :: Maybe Text
  , orderReferenceSalesOrderId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data PrecedingInvoice = PrecedingInvoice
  { precedingInvoiceId :: Maybe Text
  , precedingInvoiceIssueDate :: Maybe Day
  }
  deriving (Eq, Show, Generic)

-- Shared substructures

data Identifier = Identifier
  { identifierValue :: Maybe Text
  , identifierSchemeId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Reference = Reference
  { referenceId :: Maybe Text
  , referenceSchemeId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Address = Address
  { addressStreet :: Maybe Text
  , addressAdditionalStreet :: Maybe Text
  , addressAddressLine :: Maybe Text
  , addressCity :: Maybe Text
  , addressPostalCode :: Maybe Text
  , addressCountrySubdivision :: Maybe Text
  , addressCountryCode :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Contact = Contact
  { contactName :: Maybe Text
  , contactPhone :: Maybe Text
  , contactEmail :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Tax = Tax
  { taxCategoryCode :: Maybe Text
  , taxRate :: Maybe Decimal
  , taxExemptionReason :: Maybe Text
  , taxExemptionReasonCode :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Attachment = Attachment
  { attachmentExternalUri :: Maybe Text
  , attachmentContent :: Maybe Text
  , attachmentMimeCode :: Maybe Text
  , attachmentFilename :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data Classification = Classification
  { classificationCode :: Maybe Text
  , classificationListId :: Maybe Text
  , classificationListVersionId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data ItemAttribute = ItemAttribute
  { itemAttributeName :: Maybe Text
  , itemAttributeValue :: Maybe Text
  }
  deriving (Eq, Show, Generic)

-- Parties

data Seller = Seller
  { sellerName :: Maybe Text
  , sellerTradeName :: Maybe Text
  , sellerEndpoint :: Maybe Identifier
  , sellerIdentifiers :: [Identifier]
  , sellerSepaCreditorId :: Maybe Text
  , sellerVatIdentifier :: Maybe Text
  , sellerTaxIdentifier :: Maybe Text
  , sellerLegalRegistrationId :: Maybe Text
  , sellerLegalRegistrationIdSchemeId :: Maybe Text
  , sellerLegalForm :: Maybe Text
  , sellerAddress :: Maybe Address
  , sellerContact :: Maybe Contact
  }
  deriving (Eq, Show, Generic)

data Buyer = Buyer
  { buyerName :: Maybe Text
  , buyerTradeName :: Maybe Text
  , buyerEndpoint :: Maybe Identifier
  , buyerIdentifier :: Maybe Identifier
  , buyerVatIdentifier :: Maybe Text
  , buyerLegalRegistrationId :: Maybe Text
  , buyerLegalRegistrationIdSchemeId :: Maybe Text
  , buyerAddress :: Maybe Address
  , buyerContact :: Maybe Contact
  }
  deriving (Eq, Show, Generic)

data Payee = Payee
  { payeeName :: Maybe Text
  , payeeIdentifier :: Maybe Identifier
  , payeeSepaCreditorId :: Maybe Text
  , payeeLegalRegistrationId :: Maybe Text
  , payeeLegalRegistrationIdSchemeId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data TaxRepresentative = TaxRepresentative
  { taxRepresentativeName :: Maybe Text
  , taxRepresentativeVatIdentifier :: Maybe Text
  , taxRepresentativeAddress :: Maybe Address
  }
  deriving (Eq, Show, Generic)

data Delivery = Delivery
  { deliveryPartyName :: Maybe Text
  , deliveryActualDate :: Maybe Day
  , deliveryLocation :: Maybe Reference
  , deliveryAddress :: Maybe Address
  }
  deriving (Eq, Show, Generic)

-- Payment, totals, and tax

data Payment = Payment
  { paymentMeansCode :: Maybe Text
  , paymentMeansText :: Maybe Text
  , paymentPaymentId :: Maybe Text
  , paymentPayeeAccounts :: [PayeeAccount]
  , paymentCard :: Maybe PaymentCard
  , paymentMandate :: Maybe PaymentMandate
  }
  deriving (Eq, Show, Generic)

data PayeeAccount = PayeeAccount
  { payeeAccountId :: Maybe Text
  , payeeAccountName :: Maybe Text
  , payeeAccountBic :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data PaymentCard = PaymentCard
  { paymentCardPrimaryAccountNumberId :: Maybe Text
  , paymentCardHolderName :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data PaymentMandate = PaymentMandate
  { paymentMandateId :: Maybe Text
  , paymentMandatePayerAccountId :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data PaymentTerms = PaymentTerms
  { paymentTermsNote :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data DocumentAllowanceCharge = DocumentAllowanceCharge
  { documentAllowanceChargeAmount :: Maybe Decimal
  , documentAllowanceChargeBaseAmount :: Maybe Decimal
  , documentAllowanceChargePercent :: Maybe Decimal
  , documentAllowanceChargeReason :: Maybe Text
  , documentAllowanceChargeReasonCode :: Maybe Text
  , documentAllowanceChargeTax :: Maybe Tax
  }
  deriving (Eq, Show, Generic)

data Totals = Totals
  { totalsLineExtensionAmount :: Maybe Decimal
  , totalsAllowanceTotalAmount :: Maybe Decimal
  , totalsChargeTotalAmount :: Maybe Decimal
  , totalsTaxExclusiveAmount :: Maybe Decimal
  , totalsTaxAmountInDocumentCurrency :: Maybe Decimal
  , totalsTaxAmountInTaxCurrency :: Maybe Decimal
  , totalsTaxInclusiveAmount :: Maybe Decimal
  , totalsPrepaidAmount :: Maybe Decimal
  , totalsPayableRoundingAmount :: Maybe Decimal
  , totalsPayableAmount :: Maybe Decimal
  }
  deriving (Eq, Show, Generic)

data VatBreakdown = VatBreakdown
  { vatBreakdownTaxableAmount :: Maybe Decimal
  , vatBreakdownTaxAmount :: Maybe Decimal
  , vatBreakdownCategoryCode :: Maybe Text
  , vatBreakdownRate :: Maybe Decimal
  , vatBreakdownExemptionReason :: Maybe Text
  , vatBreakdownExemptionReasonCode :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data SupportingDocument = SupportingDocument
  { supportingDocumentId :: Maybe Text
  , supportingDocumentDescription :: Maybe Text
  , supportingDocumentEmbedded :: Maybe Attachment
  }
  deriving (Eq, Show, Generic)

-- Invoice lines

data Line = Line
  { lineId :: Maybe Text
  , lineNote :: Maybe Text
  , lineObjectReference :: Maybe Reference
  , lineQuantity :: Maybe Decimal
  , lineQuantityUnitCode :: Maybe Text
  , lineLineExtensionAmount :: Maybe Decimal
  , lineAccountingCost :: Maybe Text
  , lineOrderLineReference :: Maybe Text
  , linePeriod :: Maybe LinePeriod
  , lineAllowances :: [LineAllowanceCharge]
  , lineCharges :: [LineAllowanceCharge]
  , linePrice :: Maybe LinePrice
  , lineVat :: Maybe LineVat
  , lineItem :: Maybe LineItem
  }
  deriving (Eq, Show, Generic)

data LinePeriod = LinePeriod
  { linePeriodStartDate :: Maybe Day
  , linePeriodEndDate :: Maybe Day
  }
  deriving (Eq, Show, Generic)

data LineAllowanceCharge = LineAllowanceCharge
  { lineAllowanceChargeAmount :: Maybe Decimal
  , lineAllowanceChargeBaseAmount :: Maybe Decimal
  , lineAllowanceChargePercent :: Maybe Decimal
  , lineAllowanceChargeReason :: Maybe Text
  , lineAllowanceChargeReasonCode :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data LinePrice = LinePrice
  { linePriceNetAmount :: Maybe Decimal
  , linePriceDiscount :: Maybe PriceDiscount
  , linePriceBaseQuantity :: Maybe Decimal
  , linePriceBaseQuantityUnitCode :: Maybe Text
  }
  deriving (Eq, Show, Generic)

data PriceDiscount = PriceDiscount
  { priceDiscountAmount :: Maybe Decimal
  , priceDiscountBaseAmount :: Maybe Decimal
  }
  deriving (Eq, Show, Generic)

data LineVat = LineVat
  { lineVatCategoryCode :: Maybe Text
  , lineVatRate :: Maybe Decimal
  }
  deriving (Eq, Show, Generic)

data LineItem = LineItem
  { lineItemName :: Maybe Text
  , lineItemDescription :: Maybe Text
  , lineItemSellersItemId :: Maybe Text
  , lineItemBuyersItemId :: Maybe Text
  , lineItemStandardId :: Maybe Text
  , lineItemStandardIdSchemeId :: Maybe Text
  , lineItemClassifications :: [Classification]
  , lineItemOriginCountryCode :: Maybe Text
  , lineItemAttributes :: [ItemAttribute]
  }
  deriving (Eq, Show, Generic)

{-# LANGUAGE OverloadedStrings #-}

module XRechnung.Ubl.Internal.Builder
  ( invoiceNamespace
  , cbcNamespace
  , cacNamespace
  , invoiceName
  , cbcName
  , cacName
  , attr
  , attrMaybe
  , document
  , element
  , elementNode
  , wrapIf
  , wrapIfAny
  , textNode
  , textElement
  , optTextNode
  , optDayNode
  , optDecimalNode
  , optAmountNode
  , optQuantityNode
  , optIdentifierNode
  , renderDecimal
  , renderDocumentText
  ) where

import qualified Data.Map.Strict as Map
import Data.Ratio (denominator, numerator)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import Data.Time (Day)
import Data.Time.Calendar (showGregorian)
import Text.XML
import Text.XML.Stream.Render.Internal (RenderSettings (..))
import XRechnung.Model (Decimal (..))

invoiceNamespace :: Text
invoiceNamespace = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"

cbcNamespace :: Text
cbcNamespace = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"

cacNamespace :: Text
cacNamespace = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"

invoiceName :: Name
invoiceName = Name "Invoice" (Just invoiceNamespace) (Just "ubl")

cbcName :: Text -> Name
cbcName localName = Name localName (Just cbcNamespace) (Just "cbc")

cacName :: Text -> Name
cacName localName = Name localName (Just cacNamespace) (Just "cac")

attr :: Text -> Text -> (Name, Text)
attr key value = (Name key Nothing Nothing, value)

attrMaybe :: Text -> Maybe Text -> [(Name, Text)]
attrMaybe key = maybe [] (\value -> [attr key value])

document :: Element -> Document
document root = Document (Prologue [] Nothing []) root []

element :: Name -> [(Name, Text)] -> [Node] -> Element
element name attrs children = Element name (Map.fromList attrs) children

elementNode :: Name -> [(Name, Text)] -> [Node] -> Node
elementNode name attrs children = NodeElement (element name attrs children)

wrapIf :: Bool -> Name -> [(Name, Text)] -> [Node] -> [Node]
wrapIf condition name attrs children =
  [elementNode name attrs children | condition]

wrapIfAny :: Name -> [(Name, Text)] -> [Node] -> [Node]
wrapIfAny name attrs children =
  wrapIf (not (null attrs) || not (null children)) name attrs children

textNode :: Text -> Node
textNode = NodeContent

textElement :: Name -> [(Name, Text)] -> Text -> Node
textElement name attrs value = elementNode name attrs [textNode value]

optTextNode :: Name -> Maybe Text -> [Node]
optTextNode name = maybe [] (\value -> [textElement name [] value])

optDayNode :: Name -> Maybe Day -> [Node]
optDayNode name = maybe [] (\value -> [textElement name [] (T.pack (showGregorian value))])

optDecimalNode :: Name -> Maybe Decimal -> [Node]
optDecimalNode name = maybe [] (\value -> [textElement name [] (renderDecimal value)])

optAmountNode :: Name -> Maybe Decimal -> Maybe Text -> [Node]
optAmountNode name value currency =
  maybe [] (\amount -> [textElement name (attrMaybe "currencyID" currency) (renderDecimal amount)]) value

optQuantityNode :: Name -> Maybe Decimal -> Maybe Text -> [Node]
optQuantityNode name value unitCode =
  maybe [] (\quantity -> [textElement name (attrMaybe "unitCode" unitCode) (renderDecimal quantity)]) value

optIdentifierNode :: Name -> Maybe Text -> Maybe Text -> [Node]
optIdentifierNode name value schemeId =
  maybe [] (\identifierValue -> [textElement name (attrMaybe "schemeID" schemeId) identifierValue]) value

renderDocumentText :: Document -> Text
renderDocumentText =
  TL.toStrict
    . renderText
      ( def
          { -- Pretty rendering injects indentation into text-only elements such as
            -- CustomizationID, which breaks KoSIT scenario matching.
            rsPretty = False
          , rsNamespaces =
              [ ("ubl", invoiceNamespace)
              , ("cac", cacNamespace)
              , ("cbc", cbcNamespace)
              ]
          }
      )

renderDecimal :: Decimal -> Text
renderDecimal (Decimal value) = renderRational value

renderRational :: Rational -> Text
renderRational value
  | reducedDenominator == 1 = T.pack (signPrefix <> renderFinite scaledNumerator scale)
  | otherwise = T.pack (show (fromRational value :: Double))
  where
    signPrefix = if value < 0 then "-" else ""
    absoluteNumerator = abs (numerator value)
    absoluteDenominator = denominator value
    (twos, withoutTwos) = factorCount 2 absoluteDenominator
    (fives, reducedDenominator) = factorCount 5 withoutTwos
    scale = max twos fives
    scaledNumerator =
      absoluteNumerator
        * 2 ^ (scale - twos)
        * 5 ^ (scale - fives)

renderFinite :: Integer -> Int -> String
renderFinite value 0 = show value
renderFinite value scale =
  let digits = show value
      padded = replicate (scale + 1 - length digits) '0' ++ digits
      splitIndex = length padded - scale
      wholePart = take splitIndex padded
      fractionalPart = drop splitIndex padded
      trimmedFraction = reverse (dropWhile (== '0') (reverse fractionalPart))
   in if null trimmedFraction
        then wholePart
        else wholePart ++ "." ++ trimmedFraction

factorCount :: Integer -> Integer -> (Int, Integer)
factorCount factor = go 0
  where
    go count current
      | current `mod` factor == 0 = go (count + 1) (current `div` factor)
      | otherwise = (count, current)

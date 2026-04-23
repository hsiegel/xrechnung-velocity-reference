{-# LANGUAGE OverloadedStrings #-}

module XRechnung.Validation
  ( Validation
  , ValidationIssue (..)
  , renderIssue
  , renderIssues
  ) where

import Data.Text (Text)

type Validation = [ValidationIssue]

data ValidationIssue = ValidationIssue
  { validationIssueRule :: Text
  , validationIssueField :: Maybe Text
  , validationIssueDescription :: Text
  }
  deriving (Eq, Show)

renderIssue :: ValidationIssue -> Text
renderIssue issue =
  validationIssueRule issue
    <> ": "
    <> case validationIssueField issue of
      Nothing -> validationIssueDescription issue
      Just field -> field <> " " <> validationIssueDescription issue

renderIssues :: Validation -> [Text]
renderIssues = map renderIssue

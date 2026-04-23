{-# LANGUAGE OverloadedStrings #-}

module Main where

import Control.Exception (IOException, displayException, try)
import qualified Data.Text as T
import qualified Data.Text.IO as TIO
import Options.Applicative
import System.Exit (ExitCode (..), exitWith)
import System.IO (stderr)
import Text.Pretty.Simple (pPrintNoColor)
import XRechnung
import XRechnung.SemanticModel.Yaml

data Command
  = VerifyCommand FilePath
  | FormatCommand FilePath
  | PrettyModelCommand FilePath
  | ToXmlCommand FilePath

main :: IO ()
main = execParser parserInfo >>= runCommand >>= exitWith

parserInfo :: ParserInfo Command
parserInfo =
  info
    (commandParser <**> helper)
    ( fullDesc
        <> progDesc "Parse, validate, normalize, and convert XRechnung invoices."
        <> header "xrechnung-haskell-verifier"
    )

commandParser :: Parser Command
commandParser =
  hsubparser $
    command "verify" (info (VerifyCommand <$> inputArgument) (progDesc "Read a UBL invoice and validate it."))
      <> command "format" (info (FormatCommand <$> inputArgument) (progDesc "Read a UBL invoice and emit normalized XML."))
      <> command "pretty-model" (info (PrettyModelCommand <$> inputArgument) (progDesc "Read a UBL invoice and pretty-print the XRechnung model."))
      <> command "to-xml" (info (ToXmlCommand <$> inputArgument) (progDesc "Read semantic-model YAML and emit UBL XML."))

inputArgument :: Parser FilePath
inputArgument =
  strArgument $
    metavar "FILE"
      <> help "Input file, or - to read from stdin"

runCommand :: Command -> IO ExitCode
runCommand command =
  case command of
    VerifyCommand inputPath -> runVerify inputPath
    FormatCommand inputPath -> runFormat inputPath
    PrettyModelCommand inputPath -> runPrettyModel inputPath
    ToXmlCommand inputPath -> runToXml inputPath

runVerify :: FilePath -> IO ExitCode
runVerify inputPath = do
  invoiceResult <- loadInvoice inputPath
  case invoiceResult of
    Left errorMessage -> do
      TIO.hPutStrLn stderr errorMessage
      pure (ExitFailure 2)
    Right invoice ->
      case verify invoice of
        [] -> do
          TIO.putStrLn "No validation issues."
          pure ExitSuccess
        issues -> do
          mapM_ (TIO.putStrLn . renderIssue) issues
          pure (ExitFailure 1)

runFormat :: FilePath -> IO ExitCode
runFormat inputPath = do
  invoiceResult <- loadInvoice inputPath
  case invoiceResult of
    Left errorMessage -> do
      TIO.hPutStrLn stderr errorMessage
      pure (ExitFailure 2)
    Right invoice -> do
      TIO.putStrLn (renderUblXml invoice)
      pure ExitSuccess

runPrettyModel :: FilePath -> IO ExitCode
runPrettyModel inputPath = do
  invoiceResult <- loadInvoice inputPath
  case invoiceResult of
    Left errorMessage -> do
      TIO.hPutStrLn stderr errorMessage
      pure (ExitFailure 2)
    Right invoice -> do
      pPrintNoColor invoice
      pure ExitSuccess

runToXml :: FilePath -> IO ExitCode
runToXml inputPath = do
  yamlResult <- loadYamlInvoice inputPath
  case yamlResult of
    Left errorMessage -> do
      TIO.hPutStrLn stderr errorMessage
      pure (ExitFailure 2)
    Right invoice -> do
      TIO.putStrLn (renderUblXml invoice)
      pure ExitSuccess

loadInvoice :: FilePath -> IO (Either T.Text XRechnung)
loadInvoice inputPath = do
  xmlResult <- readTextInput inputPath
  pure $
    case xmlResult of
      Left errorMessage -> Left errorMessage
      Right xmlText ->
        case parseUblXml xmlText of
          Left decodeError -> Left (renderXmlDecodeError decodeError)
          Right invoice -> Right invoice

loadYamlInvoice :: FilePath -> IO (Either T.Text XRechnung)
loadYamlInvoice inputPath = do
  yamlResult <- readTextInput inputPath
  pure $
    case yamlResult of
      Left errorMessage -> Left errorMessage
      Right yamlText ->
        case parseSemanticModelYaml yamlText of
          Left decodeError -> Left (renderYamlDecodeError decodeError)
          Right invoice -> Right invoice

readTextInput :: FilePath -> IO (Either T.Text T.Text)
readTextInput "-" = Right <$> TIO.getContents
readTextInput inputPath = do
  result <- try (TIO.readFile inputPath) :: IO (Either IOException T.Text)
  pure $
    case result of
      Left ioError -> Left ("Unable to read " <> fromString inputPath <> ": " <> fromString (displayException ioError))
      Right xmlText -> Right xmlText

fromString :: String -> T.Text
fromString = T.pack

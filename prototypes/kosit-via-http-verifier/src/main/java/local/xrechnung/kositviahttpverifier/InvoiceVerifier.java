package local.xrechnung.kositviahttpverifier;

public interface InvoiceVerifier {

  VerificationResult verify(VerificationRequest request) throws VerificationException;
}

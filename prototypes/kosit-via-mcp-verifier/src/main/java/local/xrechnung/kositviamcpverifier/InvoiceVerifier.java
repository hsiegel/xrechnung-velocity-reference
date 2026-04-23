package local.xrechnung.kositviamcpverifier;

public interface InvoiceVerifier {

  VerificationResult verify(VerificationRequest request) throws VerificationException;
}

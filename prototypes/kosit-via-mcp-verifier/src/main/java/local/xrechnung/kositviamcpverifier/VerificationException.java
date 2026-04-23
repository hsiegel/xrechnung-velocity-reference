package local.xrechnung.kositviamcpverifier;

public class VerificationException extends Exception {

  private final VerificationTechnicalFailure failure;

  VerificationException(VerificationTechnicalFailure failure) {
    super(failure.getMessage());
    this.failure = failure;
  }

  VerificationException(VerificationTechnicalFailure failure, Throwable cause) {
    super(failure.getMessage(), cause);
    this.failure = failure;
  }

  public VerificationTechnicalFailure getFailure() {
    return failure;
  }
}

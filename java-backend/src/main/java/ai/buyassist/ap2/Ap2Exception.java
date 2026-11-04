package ai.buyassist.ap2;

/** Raised when the AP2 service cannot be reached or returns an error. */
public class Ap2Exception extends RuntimeException {
    public Ap2Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public Ap2Exception(String message) {
        super(message);
    }
}

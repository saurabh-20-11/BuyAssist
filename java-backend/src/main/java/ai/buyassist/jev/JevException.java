package ai.buyassist.jev;

/** Raised when a Jev call cannot complete (auth, validation, or exhausted retries). */
public class JevException extends RuntimeException {

    public JevException(String message) {
        super(message);
    }

    public JevException(String message, Throwable cause) {
        super(message, cause);
    }
}

package ai.buyassist.procurement;

/**
 * Result of one hard-constraint check.
 *
 * @param name    constraint name, e.g. "budget", "quantity", "USB-C", "warranty"
 * @param passed  whether the candidate satisfied it
 * @param detail  human-readable explanation for the audit trail / Decision Trace
 */
public record ConstraintCheck(String name, boolean passed, String detail) {

    public static ConstraintCheck pass(String name, String detail) {
        return new ConstraintCheck(name, true, detail);
    }

    public static ConstraintCheck fail(String name, String detail) {
        return new ConstraintCheck(name, false, detail);
    }
}

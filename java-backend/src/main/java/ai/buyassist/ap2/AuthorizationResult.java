package ai.buyassist.ap2;

import java.util.Map;

/**
 * Result of an authorize action: the payment outcome + receipt (if any), plus
 * the AP2 operation data for the audit trail.
 *
 * @param authorized   true if simulated payment succeeded
 * @param status       payment status (AUTHORIZED / DECLINED / ... / REFUSED)
 * @param detail       human-readable explanation
 * @param receipt      receipt map when authorized, else null
 * @param correlationId links to the audit timeline
 */
public record AuthorizationResult(
        boolean authorized,
        String status,
        String detail,
        Map<String, Object> receipt,
        String correlationId
) {
    public static AuthorizationResult refused(String detail, String correlationId) {
        return new AuthorizationResult(false, "REFUSED", detail, null, correlationId);
    }
}

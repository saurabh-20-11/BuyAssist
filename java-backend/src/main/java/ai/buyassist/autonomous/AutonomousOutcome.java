package ai.buyassist.autonomous;

import java.util.List;
import java.util.Map;

/**
 * Result of running an autonomous mandate check.
 *
 * @param decision       one of: NO_QUALIFYING_OFFER | ESCALATED | PURCHASED | DECLINED | ERROR
 * @param reason         human-readable explanation
 * @param qualifyingOffer the offer considered (null if none)
 * @param receipt        receipt map if a purchase executed, else null
 * @param correlationId  links to the audit timeline
 */
public record AutonomousOutcome(
        String decision,
        String reason,
        Map<String, Object> qualifyingOffer,
        Map<String, Object> receipt,
        String correlationId
) {
    public static AutonomousOutcome noOffer(String reason, String corr) {
        return new AutonomousOutcome("NO_QUALIFYING_OFFER", reason, null, null, corr);
    }

    public static AutonomousOutcome escalated(String reason, Map<String, Object> offer, String corr) {
        return new AutonomousOutcome("ESCALATED", reason, offer, null, corr);
    }

    public static AutonomousOutcome purchased(String reason, Map<String, Object> offer,
                                              Map<String, Object> receipt, String corr) {
        return new AutonomousOutcome("PURCHASED", reason, offer, receipt, corr);
    }

    public static AutonomousOutcome declined(String reason, Map<String, Object> offer, String corr) {
        return new AutonomousOutcome("DECLINED", reason, offer, null, corr);
    }
}

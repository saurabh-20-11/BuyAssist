package ai.buyassist.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One entry in a purchase's audit timeline.
 *
 * @param timestamp when the event occurred
 * @param stage     short stage label (e.g. "intent_received", "payment_simulated")
 * @param layer     which architectural layer produced it (analytics / decision / policy / ap2)
 * @param detail    human-readable description
 * @param data      optional structured payload for inspection
 */
public record AuditEvent(
        Instant timestamp,
        String stage,
        String layer,
        String detail,
        Map<String, Object> data
) {
    public AuditEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}

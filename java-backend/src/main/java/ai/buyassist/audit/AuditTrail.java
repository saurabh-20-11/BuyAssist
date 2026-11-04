package ai.buyassist.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory audit store keyed by a correlation id, so one purchase (evaluate →
 * prepare → authorize, or an autonomous mandate's lifecycle) has one coherent,
 * ordered timeline. Suitable for the portfolio/demo build; a production system
 * would persist this.
 */
@Service
public class AuditTrail {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AuditEvent>> trails =
            new ConcurrentHashMap<>();

    /** Record an event under a correlation id. */
    public void record(String correlationId, String stage, String layer,
                       String detail, Map<String, Object> data) {
        trails.computeIfAbsent(correlationId, k -> new CopyOnWriteArrayList<>())
                .add(new AuditEvent(Instant.now(), stage, layer, detail, data));
    }

    public void record(String correlationId, String stage, String layer, String detail) {
        record(correlationId, stage, layer, detail, Map.of());
    }

    /** The ordered timeline for a correlation id (empty if unknown). */
    public List<AuditEvent> timeline(String correlationId) {
        return List.copyOf(trails.getOrDefault(correlationId, new CopyOnWriteArrayList<>()));
    }

    public boolean exists(String correlationId) {
        return trails.containsKey(correlationId);
    }
}

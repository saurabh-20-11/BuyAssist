package ai.buyassist.scoring;

import ai.buyassist.procurement.Candidate;

import java.util.Map;

/**
 * A candidate plus its deterministic score breakdown.
 *
 * @param candidate   the underlying offer
 * @param total       overall score in [0,100]
 * @param components  per-dimension contributions in [0,100] for transparency
 */
public record ScoredCandidate(
        Candidate candidate,
        double total,
        Map<String, Double> components
) {
    public ScoredCandidate {
        components = components == null ? Map.of() : Map.copyOf(components);
    }
}

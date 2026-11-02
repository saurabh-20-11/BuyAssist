package ai.buyassist.jev;

import java.util.Map;

/**
 * Typed, audit-ready result of a Jev decision.
 *
 * <p>Mirrors the four bounded questions BuyAssist asks. Every field here is
 * decision-support only; the policy engine consumes this result and applies the
 * configured thresholds to decide the application action. Jev never acts on its
 * own authority.
 *
 * @param bestMatchChoice         winning option from the Choice question (a candidate name or "none")
 * @param bestMatchConfidence     calibrated confidence [0,1] of the Choice
 * @param bestMatchProbabilities  full probability distribution over options
 * @param matchQualityIndex       probability-weighted Score index (0=poor..3=excellent)
 * @param matchQualityLegend      index -> label mapping returned by Jev
 * @param matchQualityConfidence  calibrated confidence [0,1] of the Score
 * @param needsClarification      Noul probability [0,1] that clarification is warranted
 * @param needsHumanReview        Noul probability [0,1] that human review is warranted
 * @param answeringModel          exact model id that answered (e.g. "jev-1.13.0"), for audit
 * @param inputTokens             token usage, for audit
 */
public record JevDecisionResult(
        String bestMatchChoice,
        double bestMatchConfidence,
        Map<String, Double> bestMatchProbabilities,
        double matchQualityIndex,
        Map<String, String> matchQualityLegend,
        double matchQualityConfidence,
        double needsClarification,
        double needsHumanReview,
        String answeringModel,
        long inputTokens
) {
    public JevDecisionResult {
        bestMatchProbabilities = bestMatchProbabilities == null
                ? Map.of() : Map.copyOf(bestMatchProbabilities);
        matchQualityLegend = matchQualityLegend == null
                ? Map.of() : Map.copyOf(matchQualityLegend);
    }

    /** Human-readable label for the match-quality index (nearest level). */
    public String matchQualityLabel() {
        int idx = (int) Math.round(matchQualityIndex);
        return matchQualityLegend.getOrDefault(String.valueOf(idx), String.valueOf(idx));
    }

    /**
     * A disabled/unavailable result used when Jev is not configured, so the
     * pipeline degrades gracefully to deterministic-only behavior.
     */
    public static JevDecisionResult unavailable() {
        return new JevDecisionResult(
                "none", 0.0, Map.of(),
                0.0, Map.of(), 0.0,
                0.0, 0.0,
                "unavailable", 0L);
    }
}

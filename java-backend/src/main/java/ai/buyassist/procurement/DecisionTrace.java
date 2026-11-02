package ai.buyassist.procurement;

import ai.buyassist.jev.JevDecisionResult;
import ai.buyassist.scoring.ScoredCandidate;

import java.util.List;

/**
 * The full, explainable record of one procurement evaluation. This is what the
 * Android "Decision Trace" screen renders and what feeds the audit trail: the
 * deterministic scores, every hard-constraint check, the chosen winner, and the
 * bounded decision-layer judgment with its probabilities/confidence.
 *
 * @param intent             the structured intent evaluated
 * @param evaluations        per-candidate hard-constraint results
 * @param ranked             deterministically scored, policy-valid candidates (best first)
 * @param deterministicTop   name of the deterministic #1 (the actual winner), or null
 * @param decisionProvider   which bounded decision model answered (audit)
 * @param decision           the bounded decision-layer result (advisory)
 */
public record DecisionTrace(
        ProcurementIntent intent,
        List<PolicyEvaluation> evaluations,
        List<ScoredCandidate> ranked,
        String deterministicTop,
        String decisionProvider,
        JevDecisionResult decision
) {
    /** True when the deterministic winner and the decision layer's pick agree. */
    public boolean decisionAgreesWithDeterministic() {
        return deterministicTop != null
                && deterministicTop.equalsIgnoreCase(decision.bestMatchChoice());
    }
}

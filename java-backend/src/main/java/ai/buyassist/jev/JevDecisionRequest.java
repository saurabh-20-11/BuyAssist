package ai.buyassist.jev;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ProcurementIntent;

import java.util.List;

/**
 * Domain-level input to the Jev decision layer.
 *
 * <p>Carries the structured procurement state (intent + the policy-valid
 * candidates only) that becomes Jev's {@code state}, plus the name of the
 * deterministic top-ranked candidate for context. The {@link JevDecisionService}
 * turns this into the typed {@code questions} map.
 *
 * <p>Note: only policy-valid candidates are passed, so Jev never promotes an
 * option that failed a hard constraint.
 */
public record JevDecisionRequest(
        ProcurementIntent intent,
        List<Candidate> policyValidCandidates,
        String deterministicTopName
) {
    public JevDecisionRequest {
        policyValidCandidates = policyValidCandidates == null
                ? List.of() : List.copyOf(policyValidCandidates);
    }
}

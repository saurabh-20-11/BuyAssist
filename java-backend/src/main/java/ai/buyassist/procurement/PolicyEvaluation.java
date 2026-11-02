package ai.buyassist.procurement;

import java.util.List;

/**
 * Deterministic policy evaluation of a single candidate against an intent.
 *
 * @param candidate the evaluated offer
 * @param checks    per-constraint results (budget, quantity, features, warranty, merchant)
 */
public record PolicyEvaluation(Candidate candidate, List<ConstraintCheck> checks) {

    public PolicyEvaluation {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    /** A candidate is policy-valid only if every hard constraint passed. */
    public boolean isPolicyValid() {
        return checks.stream().allMatch(ConstraintCheck::passed);
    }
}

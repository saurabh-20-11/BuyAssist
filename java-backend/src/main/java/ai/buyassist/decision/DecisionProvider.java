package ai.buyassist.decision;

import ai.buyassist.jev.JevDecisionRequest;
import ai.buyassist.jev.JevDecisionResult;

/**
 * The bounded semantic decision layer, abstracted so its backing model is
 * swappable via configuration.
 *
 * <p>Whatever the implementation (Jev's calibrated System One model, or Gemini
 * constrained to structured output), it answers the same four bounded questions
 * and returns a typed {@link JevDecisionResult}. Nothing downstream depends on
 * how the answer was produced.
 *
 * <p>Critically, no implementation is permitted to grant payment authority or
 * override a hard constraint — this layer is advisory decision-support only. The
 * deterministic policy engine applies the configured thresholds to the result.
 */
public interface DecisionProvider {

    /** A short identifier for the backing model, recorded in the audit trail. */
    String providerName();

    /** Answer the four bounded questions for this procurement decision. */
    JevDecisionResult decide(JevDecisionRequest request);
}

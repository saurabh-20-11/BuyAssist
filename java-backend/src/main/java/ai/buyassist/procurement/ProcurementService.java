package ai.buyassist.procurement;

import ai.buyassist.decision.DecisionProvider;
import ai.buyassist.jev.JevDecisionRequest;
import ai.buyassist.jev.JevDecisionResult;
import ai.buyassist.scoring.ScoredCandidate;
import ai.buyassist.scoring.ScoringEngine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Orchestrates the three-signal evaluation pipeline for a procurement request:
 *
 * <ol>
 *   <li>Deterministic quantitative scoring (ScoringEngine)</li>
 *   <li>Hard-constraint policy gate (PolicyEngine) — only valid candidates proceed</li>
 *   <li>Bounded semantic decision (DecisionProvider: Gemini or Jev)</li>
 * </ol>
 *
 * <p>The winner is the deterministic #1 among policy-valid candidates. The
 * decision layer's opinion is advisory: it is surfaced in the Decision Trace and
 * can trigger clarification / human-review, but it never promotes a candidate
 * that failed a hard constraint and never authorizes payment.
 */
@Service
public class ProcurementService {

    private final ScoringEngine scoringEngine;
    private final PolicyEngine policyEngine;
    private final DecisionProvider decisionProvider;

    public ProcurementService(ScoringEngine scoringEngine,
                              PolicyEngine policyEngine,
                              DecisionProvider decisionProvider) {
        this.scoringEngine = scoringEngine;
        this.policyEngine = policyEngine;
        this.decisionProvider = decisionProvider;
    }

    public DecisionTrace evaluate(ProcurementIntent intent,
                                  List<Candidate> candidates,
                                  Set<String> allowedMerchants) {
        // 1. Per-candidate hard-constraint evaluation (for the trace).
        List<PolicyEvaluation> evaluations = candidates.stream()
                .map(c -> policyEngine.evaluate(intent, c, allowedMerchants))
                .toList();

        // 2. Keep only policy-valid candidates.
        List<Candidate> valid = evaluations.stream()
                .filter(PolicyEvaluation::isPolicyValid)
                .map(PolicyEvaluation::candidate)
                .toList();

        // 3. Deterministic ranking over the valid set.
        List<ScoredCandidate> ranked = scoringEngine.rank(intent, valid);
        String deterministicTop = ranked.isEmpty() ? null : ranked.get(0).candidate().name();

        // 4. Bounded semantic decision over the valid set.
        JevDecisionResult decision = decisionProvider.decide(
                new JevDecisionRequest(intent, valid, deterministicTop));

        return new DecisionTrace(
                intent, evaluations, ranked, deterministicTop,
                decisionProvider.providerName(), decision);
    }
}

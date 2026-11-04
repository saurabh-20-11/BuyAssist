package ai.buyassist.procurement;

import ai.buyassist.audit.AuditTrail;
import ai.buyassist.decision.DecisionProvider;
import ai.buyassist.jev.JevDecisionRequest;
import ai.buyassist.jev.JevDecisionResult;
import ai.buyassist.scoring.ScoredCandidate;
import ai.buyassist.scoring.ScoringEngine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final AuditTrail auditTrail;

    public ProcurementService(ScoringEngine scoringEngine,
                              PolicyEngine policyEngine,
                              DecisionProvider decisionProvider,
                              AuditTrail auditTrail) {
        this.scoringEngine = scoringEngine;
        this.policyEngine = policyEngine;
        this.decisionProvider = decisionProvider;
        this.auditTrail = auditTrail;
    }

    public DecisionTrace evaluate(ProcurementIntent intent,
                                  List<Candidate> candidates,
                                  Set<String> allowedMerchants) {
        String correlationId = "txn-" + UUID.randomUUID().toString().substring(0, 12);
        auditTrail.record(correlationId, "intent_received", "input",
                "Procurement intent received",
                Map.of("product", intent.product(), "quantity", intent.quantity(),
                        "budget", intent.budget(), "requiredFeatures", intent.requiredFeatures(),
                        "minimumWarrantyYears", intent.minimumWarrantyYears()));

        // 1. Per-candidate hard-constraint evaluation (for the trace).
        List<PolicyEvaluation> evaluations = candidates.stream()
                .map(c -> policyEngine.evaluate(intent, c, allowedMerchants))
                .toList();

        // 2. Keep only policy-valid candidates.
        List<Candidate> valid = evaluations.stream()
                .filter(PolicyEvaluation::isPolicyValid)
                .map(PolicyEvaluation::candidate)
                .toList();
        auditTrail.record(correlationId, "policy_gate", "policy",
                "%d of %d candidates passed every hard constraint".formatted(valid.size(), candidates.size()),
                Map.of("validCount", valid.size(), "totalCount", candidates.size()));

        // 3. Deterministic ranking over the valid set.
        List<ScoredCandidate> ranked = scoringEngine.rank(intent, valid);
        String deterministicTop = ranked.isEmpty() ? null : ranked.get(0).candidate().name();
        auditTrail.record(correlationId, "scores_computed", "analytics",
                "Deterministic scoring complete; winner=" + deterministicTop,
                Map.of("top", String.valueOf(deterministicTop),
                        "topScore", ranked.isEmpty() ? 0.0 : ranked.get(0).total()));

        // 4. Bounded semantic decision over the valid set.
        JevDecisionResult decision = decisionProvider.decide(
                new JevDecisionRequest(intent, valid, deterministicTop));
        auditTrail.record(correlationId, "decision_layer", "decision",
                "Bounded decision via " + decisionProvider.providerName(),
                Map.of("provider", decisionProvider.providerName(),
                        "model", decision.answeringModel(),
                        "bestMatch", decision.bestMatchChoice(),
                        "bestMatchConfidence", decision.bestMatchConfidence(),
                        "matchQuality", decision.matchQualityLabel(),
                        "needsClarification", decision.needsClarification(),
                        "needsHumanReview", decision.needsHumanReview()));

        return new DecisionTrace(
                intent, evaluations, ranked, deterministicTop,
                decisionProvider.providerName(), decision, correlationId);
    }
}

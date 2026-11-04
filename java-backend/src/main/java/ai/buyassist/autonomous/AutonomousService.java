package ai.buyassist.autonomous;

import ai.buyassist.ap2.Ap2Client;
import ai.buyassist.ap2.Ap2Requests;
import ai.buyassist.ap2.Ap2Response;
import ai.buyassist.audit.AuditTrail;
import ai.buyassist.decision.DecisionProvider;
import ai.buyassist.jev.JevDecisionRequest;
import ai.buyassist.jev.JevDecisionResult;
import ai.buyassist.jev.JevProperties;
import ai.buyassist.merchant.MerchantService;
import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.PolicyEngine;
import ai.buyassist.procurement.ProcurementIntent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-not-present (autonomous) purchasing.
 *
 * <p>Structural invariant: Jev/Gemini can BLOCK or FLAG an autonomous purchase
 * but can never CAUSE one. A purchase executes only if ALL of these hold:
 * <ol>
 *   <li>every hard mandate constraint passes deterministically (Java),</li>
 *   <li>the decision layer clears the review/confidence gate, AND</li>
 *   <li>AP2 verifies the user's prior IntentMandate authorization.</li>
 * </ol>
 * Fail-safe: if the decision layer is unavailable or cannot clear the gate, the
 * flow ESCALATES to the user rather than auto-purchasing.
 */
@Service
public class AutonomousService {

    private final ConcurrentHashMap<String, AutonomousMandate> mandates = new ConcurrentHashMap<>();

    private final MerchantService merchantService;
    private final PolicyEngine policyEngine;
    private final DecisionProvider decisionProvider;
    private final Ap2Client ap2Client;
    private final AuditTrail auditTrail;
    private final JevProperties.Thresholds thresholds;

    public AutonomousService(MerchantService merchantService,
                             PolicyEngine policyEngine,
                             DecisionProvider decisionProvider,
                             Ap2Client ap2Client,
                             AuditTrail auditTrail,
                             JevProperties jevProperties) {
        this.merchantService = merchantService;
        this.policyEngine = policyEngine;
        this.decisionProvider = decisionProvider;
        this.ap2Client = ap2Client;
        this.auditTrail = auditTrail;
        this.thresholds = jevProperties.thresholds();
    }

    /** Create an open mandate (not yet active) and its AP2 IntentMandate. */
    public AutonomousMandate createMandate(AutonomousMandate draft) {
        String id = "am-" + UUID.randomUUID().toString().substring(0, 12);
        Ap2Requests.IntentMandate body = new Ap2Requests.IntentMandate(
                draft.product() + " x" + draft.quantity(),
                draft.allowedMerchants().isEmpty() ? null : draft.allowedMerchants(),
                draft.maxTotal(), "USD", draft.requiredFeatures(),
                draft.minimumWarrantyYears(), draft.quantity(), draft.expiryDays(),
                false);
        Ap2Response resp = ap2Client.intentMandate(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) resp.dataOrEmpty().get("intent_mandate");

        AutonomousMandate stored = new AutonomousMandate(
                id, draft.product(), draft.quantity(), draft.maxTotal(), draft.sizeInches(),
                draft.requiredFeatures(), draft.minimumWarrantyYears(), draft.allowedMerchants(),
                draft.expiryDays(), intent, false, false);
        mandates.put(id, stored);
        auditTrail.record(id, "autonomous_mandate_created", "ap2",
                "Open IntentMandate created (awaiting user authorization)",
                Map.of("maxTotal", draft.maxTotal(), "quantity", draft.quantity()));
        return stored;
    }

    public AutonomousMandate authorizeMandate(String id) {
        AutonomousMandate m = require(id);
        AutonomousMandate active = m.withActive(true);
        mandates.put(id, active);
        auditTrail.record(id, "autonomous_mandate_authorized", "ap2",
                "User explicitly authorized the autonomous mandate", Map.of());
        return active;
    }

    public AutonomousMandate get(String id) {
        return require(id);
    }

    /**
     * Run the autonomous check against current catalog prices. Executes a
     * purchase only if the deterministic gate, the decision gate, and AP2
     * verification all pass; otherwise escalates or reports no offer.
     */
    @SuppressWarnings("unchecked")
    public AutonomousOutcome runCheck(String id) {
        AutonomousMandate m = require(id);
        String corr = id;  // mandate id doubles as the correlation id for its lifecycle

        if (!m.active()) {
            return AutonomousOutcome.escalated("Mandate not yet authorized by the user.", null, corr);
        }
        if (m.closed()) {
            return AutonomousOutcome.noOffer("Mandate already closed.", corr);
        }

        ProcurementIntent intent = new ProcurementIntent(
                m.product(), m.quantity(), m.maxTotal(), m.requiredFeatures(),
                m.minimumWarrantyYears(), "autonomous mandate");
        Set<String> allowed = m.allowedMerchants().isEmpty() ? null : Set.copyOf(m.allowedMerchants());

        // 1. Deterministic hard gate against current catalog offers.
        List<Candidate> candidates = merchantService.candidatesFor(intent, m.sizeInches());
        List<Candidate> valid = candidates.stream()
                .filter(c -> policyEngine.evaluate(intent, c, allowed).isPolicyValid())
                .toList();
        auditTrail.record(corr, "autonomous_hard_gate", "policy",
                "%d qualifying offers under max_total %.2f".formatted(valid.size(), m.maxTotal()),
                Map.of("qualifying", valid.size()));

        if (valid.isEmpty()) {
            return AutonomousOutcome.noOffer(
                    "No current offer satisfies every hard constraint (incl. price <= max_total).", corr);
        }

        // Cheapest qualifying offer is the candidate for purchase.
        Candidate offer = valid.stream()
                .min((a, b) -> Double.compare(a.totalPrice(), b.totalPrice())).orElseThrow();
        Map<String, Object> offerMap = Map.of(
                "name", offer.name(), "merchant", offer.merchant(),
                "total", offer.totalPrice(), "warrantyYears", offer.warrantyYears());

        // 2. Decision layer on the qualifying offer.
        JevDecisionResult decision = decisionProvider.decide(
                new JevDecisionRequest(intent, valid, offer.name()));
        auditTrail.record(corr, "autonomous_decision", "decision",
                "Decision via " + decisionProvider.providerName(),
                Map.of("model", decision.answeringModel(),
                        "bestMatch", decision.bestMatchChoice(),
                        "confidence", decision.bestMatchConfidence(),
                        "needsHumanReview", decision.needsHumanReview()));

        // 3. Review/confidence gate with FAIL-SAFE escalation.
        boolean unavailable = "unavailable".equals(decision.answeringModel());
        if (unavailable) {
            auditTrail.record(corr, "autonomous_escalated", "policy",
                    "Decision layer unavailable; failing safe to human review", Map.of());
            return AutonomousOutcome.escalated(
                    "Decision layer unavailable — escalating for human review (fail-safe; no auto-purchase).",
                    offerMap, corr);
        }
        if (decision.needsHumanReview() >= thresholds.needsHumanReview()) {
            auditTrail.record(corr, "autonomous_escalated", "policy",
                    "Human-review probability above threshold", Map.of(
                            "needsHumanReview", decision.needsHumanReview(),
                            "threshold", thresholds.needsHumanReview()));
            return AutonomousOutcome.escalated(
                    "Decision layer flagged this for human review (needs_human_review %.2f >= %.2f)."
                            .formatted(decision.needsHumanReview(), thresholds.needsHumanReview()),
                    offerMap, corr);
        }
        if (decision.bestMatchConfidence() < thresholds.bestMatchConfidence()) {
            auditTrail.record(corr, "autonomous_escalated", "policy",
                    "Best-match confidence below threshold", Map.of(
                            "confidence", decision.bestMatchConfidence(),
                            "threshold", thresholds.bestMatchConfidence()));
            return AutonomousOutcome.escalated(
                    "Decision confidence too low to auto-purchase (%.2f < %.2f)."
                            .formatted(decision.bestMatchConfidence(), thresholds.bestMatchConfidence()),
                    offerMap, corr);
        }

        // 4. AP2 verifies the user's prior IntentMandate authorization.
        Ap2Response verifyIntent = ap2Client.verify(new Ap2Requests.Verify("intent", m.intentMandate()));
        if (!verifyIntent.ok()) {
            auditTrail.record(corr, "autonomous_intent_verify_failed", "ap2",
                    String.valueOf(verifyIntent.detail()), Map.of());
            return AutonomousOutcome.escalated(
                    "AP2 could not verify the stored authorization (expired/invalid).", offerMap, corr);
        }
        auditTrail.record(corr, "autonomous_intent_verified", "ap2",
                "AP2 verified the user's prior IntentMandate", Map.of());

        // 5. Build checkout + payment mandates, verify, pay.
        String productId = extractId(offer.name());
        String cartId = "cart-" + UUID.randomUUID().toString().substring(0, 12);
        ap2Client.checkoutMandate(new Ap2Requests.CheckoutMandate(
                cartId, offer.merchant(), "USD",
                List.of(new Ap2Requests.LineItem(offer.name(), offer.unitPrice(), offer.quantity())),
                offer.totalPrice(), false, 15));

        String pmId = "pm-" + UUID.randomUUID().toString().substring(0, 12);
        Ap2Requests.PaymentMandate pm = new Ap2Requests.PaymentMandate(
                cartId, pmId, offer.merchant(), "USD", offer.totalPrice(), "PROCUREMENT",
                "Procurement Account");
        Ap2Response created = ap2Client.paymentMandate(pm);
        Ap2Response verified = ap2Client.verify(new Ap2Requests.Verify("payment",
                created.dataOrEmpty().get("payment_mandate")));
        if (!verified.ok()) {
            return AutonomousOutcome.escalated("Payment mandate verification failed.", offerMap, corr);
        }

        Ap2Response paid = ap2Client.pay(pm);
        Map<String, Object> data = paid.dataOrEmpty();
        Map<String, Object> payment = (Map<String, Object>) data.getOrDefault("payment", Map.of());
        Map<String, Object> receipt = (Map<String, Object>) data.get("receipt");
        String status = String.valueOf(payment.getOrDefault("status", paid.ok() ? "AUTHORIZED" : "DECLINED"));

        auditTrail.record(corr, "autonomous_payment_simulated", "ap2",
                "Autonomous payment: " + status, Map.of("status", status));

        if (paid.ok() && receipt != null) {
            mandates.put(id, m.withClosed(true));
            auditTrail.record(corr, "autonomous_receipt_created", "ap2",
                    "Autonomous purchase complete", Map.of("receipt", receipt));
            return AutonomousOutcome.purchased(
                    "All gates passed; autonomous purchase executed.", offerMap, receipt, corr);
        }
        return AutonomousOutcome.declined("Simulated payment did not authorize: " + paid.detail(),
                offerMap, corr);
    }

    private String extractId(String candidateName) {
        int open = candidateName.lastIndexOf('(');
        int close = candidateName.lastIndexOf(')');
        return (open >= 0 && close > open) ? candidateName.substring(open + 1, close) : candidateName;
    }

    private AutonomousMandate require(String id) {
        AutonomousMandate m = mandates.get(id);
        if (m == null) {
            throw new IllegalArgumentException("Unknown autonomous mandate: " + id);
        }
        return m;
    }
}

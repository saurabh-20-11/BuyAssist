package ai.buyassist.ap2;

import ai.buyassist.merchant.MerchantService;
import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ConstraintCheck;
import ai.buyassist.procurement.PolicyEngine;
import ai.buyassist.procurement.PolicyEvaluation;
import ai.buyassist.procurement.ProcurementIntent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the human-present authorization flow between the backend and the AP2
 * service.
 *
 * <p>Security-critical invariant: all transaction amounts are rebuilt from the
 * catalog by the server. The client identifies the chosen product by id and the
 * intent constraints; it never supplies prices. Before any payment, the chosen
 * candidate is re-checked against the deterministic PolicyEngine. If that
 * re-check fails, authorization is refused — payment is never attempted.
 */
@Service
public class CheckoutService {

    private final MerchantService merchantService;
    private final PolicyEngine policyEngine;
    private final Ap2Client ap2Client;

    public CheckoutService(MerchantService merchantService,
                           PolicyEngine policyEngine,
                           Ap2Client ap2Client) {
        this.merchantService = merchantService;
        this.policyEngine = policyEngine;
        this.ap2Client = ap2Client;
    }

    /**
     * Build the deterministic Trusted Surface for a chosen product and create a
     * CartMandate via AP2. Returns the authoritative purchase details.
     */
    public TrustedSurface prepare(ProcurementIntent intent, String productId, String correlationId) {
        Candidate candidate = merchantService.candidateById(productId, intent.quantity())
                .orElseThrow(() -> new Ap2Exception("Unknown product id: " + productId));

        // Re-assert hard constraints deterministically.
        PolicyEvaluation eval = policyEngine.evaluate(intent, candidate, null);
        List<ConstraintCheck> checks = eval.checks();
        boolean authorizable = eval.isPolicyValid();

        String cartId = "cart-" + UUID.randomUUID().toString().substring(0, 12);
        String cartExpiry = null;

        if (authorizable) {
            Ap2Requests.CheckoutMandate body = new Ap2Requests.CheckoutMandate(
                    cartId,
                    candidate.merchant(),
                    "USD",
                    List.of(new Ap2Requests.LineItem(candidate.name(), candidate.unitPrice(), candidate.quantity())),
                    candidate.totalPrice(),
                    true,
                    15);
            Ap2Response resp = ap2Client.checkoutMandate(body);
            Object expiry = resp.dataOrEmpty().get("cart_expiry");
            cartExpiry = expiry == null ? null : expiry.toString();
        }

        return new TrustedSurface(
                cartId, productId, candidate.name(), candidate.merchant(),
                candidate.unitPrice(), candidate.quantity(), candidate.totalPrice(),
                "USD", checks, authorizable, cartExpiry, correlationId);
    }

    /**
     * Authorize the purchase: re-check policy, create+verify the PaymentMandate,
     * then run simulated payment and return the receipt. The chosen product is
     * re-resolved from the catalog so the client cannot alter the amount.
     */
    @SuppressWarnings("unchecked")
    public AuthorizationResult authorize(ProcurementIntent intent, String productId,
                                         String cartId, String correlationId) {
        Candidate candidate = merchantService.candidateById(productId, intent.quantity())
                .orElseThrow(() -> new Ap2Exception("Unknown product id: " + productId));

        // Deterministic re-check — the authoritative gate before any spend.
        PolicyEvaluation eval = policyEngine.evaluate(intent, candidate, null);
        if (!eval.isPolicyValid()) {
            String failed = eval.checks().stream()
                    .filter(c -> !c.passed())
                    .map(c -> c.name() + " (" + c.detail() + ")")
                    .reduce((a, b) -> a + "; " + b).orElse("unknown");
            return AuthorizationResult.refused(
                    "Policy re-check failed; payment refused: " + failed, correlationId);
        }

        String paymentMandateId = "pm-" + UUID.randomUUID().toString().substring(0, 12);
        Ap2Requests.PaymentMandate pm = new Ap2Requests.PaymentMandate(
                cartId, paymentMandateId, candidate.merchant(), "USD",
                candidate.totalPrice(), "CARD", "Corporate Visa 4242");

        // Create the payment mandate.
        Ap2Response created = ap2Client.paymentMandate(pm);
        if (!created.ok()) {
            return AuthorizationResult.refused(
                    "Payment mandate creation failed: " + created.detail(), correlationId);
        }

        // Verify the payment mandate before paying.
        Object mandateObj = created.dataOrEmpty().get("payment_mandate");
        Ap2Response verified = ap2Client.verify(new Ap2Requests.Verify("payment", mandateObj));
        if (!verified.ok()) {
            return AuthorizationResult.refused(
                    "Payment mandate verification failed: " + verified.detail(), correlationId);
        }

        // Run simulated payment + receipt.
        Ap2Response paid = ap2Client.pay(pm);
        Map<String, Object> data = paid.dataOrEmpty();
        Map<String, Object> payment = (Map<String, Object>) data.getOrDefault("payment", Map.of());
        Map<String, Object> receipt = (Map<String, Object>) data.get("receipt");
        String status = payment.getOrDefault("status", paid.ok() ? "AUTHORIZED" : "DECLINED").toString();

        return new AuthorizationResult(paid.ok(), status, paid.detail(), receipt, correlationId);
    }
}

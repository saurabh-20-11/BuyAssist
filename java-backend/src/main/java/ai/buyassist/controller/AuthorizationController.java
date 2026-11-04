package ai.buyassist.controller;

import ai.buyassist.ap2.AuthorizationResult;
import ai.buyassist.ap2.CheckoutService;
import ai.buyassist.ap2.TrustedSurface;
import ai.buyassist.procurement.ProcurementIntent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Human-present authorization endpoints. The client identifies the chosen
 * product by id and restates the intent constraints; the server rebuilds all
 * transaction amounts from the catalog. No client-supplied price is trusted.
 */
@RestController
@RequestMapping("/api/authorization")
public class AuthorizationController {

    private final CheckoutService checkoutService;

    public AuthorizationController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /** Request carrying the intent + chosen product id (+ optional correlation id). */
    public record AuthRequest(
            @NotBlank String product,
            @Min(1) int quantity,
            @Positive double budget,
            List<String> requiredFeatures,
            @Min(0) int minimumWarrantyYears,
            String notes,
            @NotBlank String productId,
            String cartId,
            String correlationId
    ) {
        public List<String> requiredFeatures() {
            return requiredFeatures == null ? List.of() : requiredFeatures;
        }

        ProcurementIntent toIntent() {
            return new ProcurementIntent(product, quantity, budget,
                    requiredFeatures(), minimumWarrantyYears, notes);
        }

        String orNewCorrelationId() {
            return (correlationId == null || correlationId.isBlank())
                    ? "txn-" + UUID.randomUUID().toString().substring(0, 12)
                    : correlationId;
        }
    }

    @PostMapping("/prepare")
    public TrustedSurface prepare(@RequestBody AuthRequest req) {
        return checkoutService.prepare(req.toIntent(), req.productId(), req.orNewCorrelationId());
    }

    @PostMapping("/authorize")
    public AuthorizationResult authorize(@RequestBody AuthRequest req) {
        return checkoutService.authorize(
                req.toIntent(), req.productId(), req.cartId(), req.orNewCorrelationId());
    }
}

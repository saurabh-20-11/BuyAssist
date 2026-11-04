package ai.buyassist.controller;

import ai.buyassist.autonomous.AutonomousMandate;
import ai.buyassist.autonomous.AutonomousOutcome;
import ai.buyassist.autonomous.AutonomousService;
import ai.buyassist.merchant.Product;
import ai.buyassist.merchant.ProductCatalog;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Human-not-present (autonomous) endpoints: create + authorize an open mandate,
 * simulate a price drop, and run the gated autonomous check.
 */
@RestController
@RequestMapping("/api/autonomous")
public class AutonomousController {

    private final AutonomousService autonomousService;
    private final ProductCatalog catalog;

    public AutonomousController(AutonomousService autonomousService, ProductCatalog catalog) {
        this.autonomousService = autonomousService;
        this.catalog = catalog;
    }

    public record MandateRequest(
            @NotBlank String product,
            @Min(1) int quantity,
            @Positive double maxTotal,
            int sizeInches,
            List<String> requiredFeatures,
            @Min(0) int minimumWarrantyYears,
            List<String> allowedMerchants,
            @Min(1) int expiryDays
    ) {
        AutonomousMandate toDraft() {
            return new AutonomousMandate(null, product, quantity, maxTotal, sizeInches,
                    requiredFeatures == null ? List.of() : requiredFeatures,
                    minimumWarrantyYears,
                    allowedMerchants == null ? List.of() : allowedMerchants,
                    expiryDays, null, false, false);
        }
    }

    public record PriceDropRequest(@NotBlank String productId, @Positive double newUnitPrice) {
    }

    /** Create an open autonomous mandate (awaiting the user's explicit authorize). */
    @PostMapping("/mandate")
    public AutonomousMandate createMandate(@RequestBody MandateRequest req) {
        return autonomousService.createMandate(req.toDraft());
    }

    /** The user explicitly authorizes the mandate, activating it. */
    @PostMapping("/mandate/{id}/authorize")
    public AutonomousMandate authorize(@PathVariable String id) {
        return autonomousService.authorizeMandate(id);
    }

    /** Run the gated autonomous check: purchases, escalates, or reports no offer. */
    @PostMapping("/mandate/{id}/evaluate")
    public AutonomousOutcome evaluate(@PathVariable String id) {
        return autonomousService.runCheck(id);
    }

    /** Simulate a merchant price change to drive the autonomous demo. */
    @PostMapping("/simulate-price-drop")
    public ResponseEntity<Map<String, Object>> simulatePriceDrop(@RequestBody PriceDropRequest req) {
        return catalog.setPrice(req.productId(), req.newUnitPrice())
                .<ResponseEntity<Map<String, Object>>>map(p -> ResponseEntity.ok(Map.of(
                        "productId", p.id(), "newUnitPrice", p.unitPrice(), "merchant", p.merchant())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

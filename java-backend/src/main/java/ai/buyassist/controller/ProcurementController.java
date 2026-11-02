package ai.buyassist.controller;

import ai.buyassist.merchant.MerchantService;
import ai.buyassist.merchant.Product;
import ai.buyassist.merchant.ProductCatalog;
import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.DecisionTrace;
import ai.buyassist.procurement.ProcurementIntent;
import ai.buyassist.procurement.ProcurementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Real procurement API. Discovers candidates from the mock catalog for the
 * requested intent, then runs the three-signal pipeline (scoring -> policy gate
 * -> bounded decision) and returns the full Decision Trace.
 */
@RestController
@RequestMapping("/api/procurement")
public class ProcurementController {

    private final MerchantService merchantService;
    private final ProcurementService procurementService;
    private final ProductCatalog catalog;

    public ProcurementController(MerchantService merchantService,
                                 ProcurementService procurementService,
                                 ProductCatalog catalog) {
        this.merchantService = merchantService;
        this.procurementService = procurementService;
        this.catalog = catalog;
    }

    /** The full catalog, for the test page to display what's available. */
    @GetMapping("/products")
    public List<Product> products() {
        return catalog.findByCategory("monitor");
    }

    /** Evaluate a procurement request end-to-end and return the Decision Trace. */
    @PostMapping("/evaluate")
    public DecisionTrace evaluate(@Valid @RequestBody ProcurementRequest request) {
        ProcurementIntent intent = new ProcurementIntent(
                request.product(),
                request.quantity(),
                request.budget(),
                request.requiredFeatures(),
                request.minimumWarrantyYears(),
                request.notes());

        List<Candidate> candidates = merchantService.candidatesFor(intent, request.sizeInches());

        Set<String> allowed = request.allowedMerchants().isEmpty()
                ? null : Set.copyOf(request.allowedMerchants());

        return procurementService.evaluate(intent, candidates, allowed);
    }
}

package ai.buyassist.merchant;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ProcurementIntent;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns the mock catalog into {@link Candidate}s for a specific procurement
 * intent: filters by category and (if the product reports a size) screen size,
 * then prices each candidate at the requested quantity.
 *
 * <p>This is pre-scoring, pre-policy discovery. It intentionally does NOT apply
 * budget/feature/warranty constraints — those are the deterministic PolicyEngine's
 * job. Size is treated as a catalog match filter (a 24" monitor is simply not the
 * product being asked for), not a policy constraint.
 */
@Service
public class MerchantService {

    private final ProductCatalog catalog;

    public MerchantService(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Build candidates for the intent. If {@code sizeInches} > 0, only products
     * of that exact size are considered.
     */
    public List<Candidate> candidatesFor(ProcurementIntent intent, int sizeInches) {
        return catalog.findByCategory("monitor").stream()
                .filter(p -> sizeInches <= 0 || p.sizeInches() == sizeInches)
                .map(p -> toCandidate(p, intent.quantity()))
                .toList();
    }

    private Candidate toCandidate(Product p, int quantity) {
        return new Candidate(
                p.brand() + " " + p.sizeInches() + "\" (" + p.id() + ")",
                p.merchant(),
                p.unitPrice(),
                quantity,
                p.warrantyYears(),
                p.deliveryDays(),
                p.features(),
                p.reliability());
    }
}

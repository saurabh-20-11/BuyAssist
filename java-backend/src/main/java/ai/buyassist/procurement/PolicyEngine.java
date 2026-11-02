package ai.buyassist.procurement;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The deterministic hard gate. This is authoritative for anything that can be
 * computed exactly: budget, quantity, required features, warranty floor, and
 * allowed merchants.
 *
 * <p>No AI model participates here. A candidate that fails any hard constraint
 * is rejected regardless of its quantitative score or any Jev judgment. This is
 * also the explicit mitigation for Jev's documented weakness at math, counting,
 * and numeric comparison — all such comparisons live in this class.
 */
@Service
public class PolicyEngine {

    /** Evaluate a candidate against the intent with no merchant restriction. */
    public PolicyEvaluation evaluate(ProcurementIntent intent, Candidate candidate) {
        return evaluate(intent, candidate, null);
    }

    /**
     * Evaluate a candidate against the intent.
     *
     * @param allowedMerchants if non-null, the candidate's merchant must be in this set
     */
    public PolicyEvaluation evaluate(ProcurementIntent intent,
                                     Candidate candidate,
                                     Set<String> allowedMerchants) {
        List<ConstraintCheck> checks = new ArrayList<>();

        // Budget: total price must not exceed the authorized budget.
        double total = candidate.totalPrice();
        checks.add(total <= intent.budget()
                ? ConstraintCheck.pass("budget",
                        "total %.2f <= budget %.2f".formatted(total, intent.budget()))
                : ConstraintCheck.fail("budget",
                        "total %.2f exceeds budget %.2f".formatted(total, intent.budget())));

        // Quantity: the offer must cover the requested quantity.
        checks.add(candidate.quantity() >= intent.quantity()
                ? ConstraintCheck.pass("quantity",
                        "offer quantity %d >= requested %d".formatted(candidate.quantity(), intent.quantity()))
                : ConstraintCheck.fail("quantity",
                        "offer quantity %d < requested %d".formatted(candidate.quantity(), intent.quantity())));

        // Required features: every required feature must be present (case-insensitive).
        List<String> have = candidate.features().stream()
                .map(f -> f.toLowerCase().trim()).collect(Collectors.toList());
        for (String required : intent.requiredFeatures()) {
            boolean present = have.contains(required.toLowerCase().trim());
            checks.add(present
                    ? ConstraintCheck.pass(required, "feature present")
                    : ConstraintCheck.fail(required, "required feature missing"));
        }

        // Warranty floor.
        checks.add(candidate.warrantyYears() >= intent.minimumWarrantyYears()
                ? ConstraintCheck.pass("warranty",
                        "warranty %dy >= minimum %dy".formatted(candidate.warrantyYears(), intent.minimumWarrantyYears()))
                : ConstraintCheck.fail("warranty",
                        "warranty %dy < minimum %dy".formatted(candidate.warrantyYears(), intent.minimumWarrantyYears())));

        // Allowed merchants (optional, used by autonomous mandates).
        if (allowedMerchants != null && !allowedMerchants.isEmpty()) {
            boolean allowed = allowedMerchants.stream()
                    .anyMatch(m -> m.equalsIgnoreCase(candidate.merchant()));
            checks.add(allowed
                    ? ConstraintCheck.pass("merchant", "merchant %s is allowed".formatted(candidate.merchant()))
                    : ConstraintCheck.fail("merchant", "merchant %s not in allowed set".formatted(candidate.merchant())));
        }

        return new PolicyEvaluation(candidate, checks);
    }

    /** Keep only candidates that pass every hard constraint. */
    public List<Candidate> filterValid(ProcurementIntent intent,
                                        List<Candidate> candidates,
                                        Set<String> allowedMerchants) {
        return candidates.stream()
                .filter(c -> evaluate(intent, c, allowedMerchants).isPolicyValid())
                .collect(Collectors.toList());
    }
}

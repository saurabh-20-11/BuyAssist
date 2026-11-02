package ai.buyassist.procurement;

import java.util.List;

/**
 * Structured purchasing intent extracted from the user's speech by Gemini.
 *
 * <p>This is the deterministic, machine-readable form of a messy spoken request.
 * All downstream hard checks (budget, quantity, features, warranty) operate on
 * these fields, never on raw prose.
 *
 * @param product              e.g. "27 inch monitor"
 * @param quantity             number of units requested
 * @param budget               maximum total spend in whole currency units
 * @param requiredFeatures     hard-required features, e.g. ["USB-C"]
 * @param minimumWarrantyYears minimum acceptable warranty in years
 * @param notes                free-text nuance Gemini captured (fed to Jev, not to hard checks)
 */
public record ProcurementIntent(
        String product,
        int quantity,
        double budget,
        List<String> requiredFeatures,
        int minimumWarrantyYears,
        String notes
) {
    public ProcurementIntent {
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
    }
}

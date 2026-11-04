package ai.buyassist.autonomous;

import java.util.List;

/**
 * A stored open authorization for the human-not-present flow. Captures the
 * constraints under which the agent may later authorize a closed checkout.
 *
 * <p>This is mutable only in its {@code active} / {@code closed} status; the
 * constraints themselves are fixed once authorized by the user.
 *
 * @param id                  server-generated mandate id
 * @param product             product description
 * @param quantity            units
 * @param maxTotal            maximum total spend (hard cap)
 * @param sizeInches          required size (0 = any)
 * @param requiredFeatures    required features
 * @param minimumWarrantyYears warranty floor
 * @param allowedMerchants    merchant allow-list (empty = any)
 * @param expiryDays          validity window in days
 * @param intentMandate       the AP2 IntentMandate object (as a map) returned by the AP2 service
 * @param active              true once the user has explicitly authorized it
 * @param closed              true once a purchase has executed or it was cancelled
 */
public record AutonomousMandate(
        String id,
        String product,
        int quantity,
        double maxTotal,
        int sizeInches,
        List<String> requiredFeatures,
        int minimumWarrantyYears,
        List<String> allowedMerchants,
        int expiryDays,
        java.util.Map<String, Object> intentMandate,
        boolean active,
        boolean closed
) {
    public AutonomousMandate withActive(boolean a) {
        return new AutonomousMandate(id, product, quantity, maxTotal, sizeInches,
                requiredFeatures, minimumWarrantyYears, allowedMerchants, expiryDays,
                intentMandate, a, closed);
    }

    public AutonomousMandate withClosed(boolean c) {
        return new AutonomousMandate(id, product, quantity, maxTotal, sizeInches,
                requiredFeatures, minimumWarrantyYears, allowedMerchants, expiryDays,
                intentMandate, active, c);
    }
}

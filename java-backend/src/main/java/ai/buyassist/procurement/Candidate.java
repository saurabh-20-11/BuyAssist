package ai.buyassist.procurement;

import java.util.List;

/**
 * A candidate offer from a mock merchant, evaluated against an intent.
 *
 * @param name             product/vendor display name, e.g. "Dell"
 * @param merchant         merchant name, e.g. "TechMart"
 * @param unitPrice        price per unit
 * @param quantity         quantity the offer covers
 * @param warrantyYears    warranty length in years
 * @param deliveryDays     estimated delivery time in days
 * @param features         feature set, e.g. ["USB-C", "HDMI"]
 * @param reliability      vendor reliability score in [0,1]
 */
public record Candidate(
        String name,
        String merchant,
        double unitPrice,
        int quantity,
        int warrantyYears,
        int deliveryDays,
        List<String> features,
        double reliability
) {
    public Candidate {
        features = features == null ? List.of() : List.copyOf(features);
    }

    /** Deterministic total price. Computed in code — never asked of a model. */
    public double totalPrice() {
        return unitPrice * quantity;
    }
}

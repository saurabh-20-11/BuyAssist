package ai.buyassist.merchant;

import java.util.List;

/**
 * A product offered by a mock merchant. This is the catalog form; it is turned
 * into a {@link ai.buyassist.procurement.Candidate} for a specific requested
 * quantity during evaluation.
 *
 * @param id             stable product id
 * @param merchant       merchant name (OfficeHub / TechMart / BusinessDirect)
 * @param brand          brand, e.g. "Dell"
 * @param name           display name
 * @param category       category, e.g. "monitor"
 * @param sizeInches     screen size in inches
 * @param unitPrice      current price per unit (may change over time)
 * @param features       feature tags, e.g. ["USB-C", "HDMI"]
 * @param warrantyYears  warranty length in years
 * @param deliveryDays   estimated delivery time in days
 * @param reliability    vendor reliability score in [0,1]
 */
public record Product(
        String id,
        String merchant,
        String brand,
        String name,
        String category,
        int sizeInches,
        double unitPrice,
        List<String> features,
        int warrantyYears,
        int deliveryDays,
        double reliability
) {
    public Product {
        features = features == null ? List.of() : List.copyOf(features);
    }
}

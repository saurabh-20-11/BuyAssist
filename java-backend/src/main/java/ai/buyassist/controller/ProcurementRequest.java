package ai.buyassist.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Inbound procurement request. In later phases this is produced by Gemini from
 * the user's speech; for now it can be posted directly (or from the web test
 * page) as structured JSON.
 *
 * @param product              product description (e.g. "27 inch monitor")
 * @param quantity             units requested
 * @param budget               maximum total spend
 * @param sizeInches           required screen size (0 = any)
 * @param requiredFeatures     hard-required features (e.g. ["USB-C"])
 * @param minimumWarrantyYears warranty floor
 * @param notes                nuance for the semantic decision layer
 * @param allowedMerchants     optional merchant allow-list (null/empty = all)
 */
public record ProcurementRequest(
        @NotBlank String product,
        @Min(1) int quantity,
        @Positive double budget,
        int sizeInches,
        List<String> requiredFeatures,
        @Min(0) int minimumWarrantyYears,
        String notes,
        List<String> allowedMerchants
) {
    public List<String> requiredFeatures() {
        return requiredFeatures == null ? List.of() : requiredFeatures;
    }

    public List<String> allowedMerchants() {
        return allowedMerchants == null ? List.of() : allowedMerchants;
    }
}

package ai.buyassist.ap2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request bodies posted to the Python AP2 service. Field names use snake_case to
 * match the service's Pydantic models exactly.
 */
public final class Ap2Requests {

    private Ap2Requests() {
    }

    public record LineItem(
            String label,
            @JsonProperty("unit_price") double unitPrice,
            int quantity
    ) {
    }

    /** Body for POST /ap2/checkout-mandate. */
    public record CheckoutMandate(
            @JsonProperty("cart_id") String cartId,
            @JsonProperty("merchant_name") String merchantName,
            String currency,
            List<LineItem> items,
            double total,
            @JsonProperty("user_cart_confirmation_required") boolean userCartConfirmationRequired,
            @JsonProperty("cart_expiry_minutes") int cartExpiryMinutes
    ) {
    }

    /** Body for POST /ap2/payment-mandate and POST /ap2/pay. */
    public record PaymentMandate(
            @JsonProperty("cart_id") String cartId,
            @JsonProperty("payment_mandate_id") String paymentMandateId,
            @JsonProperty("merchant_name") String merchantName,
            String currency,
            double total,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("payment_method_label") String paymentMethodLabel
    ) {
    }

    /** Body for POST /ap2/intent-mandate (autonomous flow). */
    public record IntentMandate(
            @JsonProperty("natural_language_description") String naturalLanguageDescription,
            List<String> merchants,
            @JsonProperty("max_total") double maxTotal,
            String currency,
            @JsonProperty("required_features") List<String> requiredFeatures,
            @JsonProperty("minimum_warranty_years") int minimumWarrantyYears,
            int quantity,
            @JsonProperty("expiry_days") int expiryDays,
            @JsonProperty("user_cart_confirmation_required") boolean userCartConfirmationRequired
    ) {
    }

    /** Body for POST /ap2/verify. */
    public record Verify(String kind, Object mandate) {
    }
}

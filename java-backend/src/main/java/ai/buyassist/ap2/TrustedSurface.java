package ai.buyassist.ap2;

import ai.buyassist.procurement.ConstraintCheck;

import java.util.List;

/**
 * The deterministic Purchase Authorization payload (the "Trusted Surface").
 *
 * <p>Every number here is computed by the server from the catalog and the user's
 * intent — no AI and no client-supplied value participates. The Android/web UI
 * renders exactly these values and must not recompute them.
 *
 * @param cartId          server-generated cart id
 * @param productId       catalog product id being purchased
 * @param itemLabel       human-readable item description
 * @param merchant        merchant name
 * @param unitPrice       authoritative unit price from the catalog
 * @param quantity        quantity
 * @param total           authoritative total (unitPrice * quantity)
 * @param currency        currency code
 * @param checks          hard-constraint check results (all must pass to authorize)
 * @param authorizable    true only if every hard constraint passes
 * @param cartExpiry      ISO-8601 cart mandate expiry from AP2
 * @param correlationId   links this authorization to its audit timeline
 */
public record TrustedSurface(
        String cartId,
        String productId,
        String itemLabel,
        String merchant,
        double unitPrice,
        int quantity,
        double total,
        String currency,
        List<ConstraintCheck> checks,
        boolean authorizable,
        String cartExpiry,
        String correlationId
) {
}

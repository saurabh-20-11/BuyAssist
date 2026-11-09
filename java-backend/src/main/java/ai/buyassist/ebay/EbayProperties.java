package ai.buyassist.ebay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the eBay Browse API, bound from {@code ebay.*}.
 *
 * <p>Credentials are read from the environment and stay server-side only. The
 * Browse API uses an application access token via the OAuth client-credentials
 * grant — no user login. When {@code catalog.source=ebay}, live listings replace
 * the mock catalog; otherwise the mock data is used.
 */
@ConfigurationProperties(prefix = "ebay")
public record EbayProperties(
        String clientId,
        String clientSecret,
        String oauthUrl,
        String browseUrl,
        String marketplaceId
) {
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}

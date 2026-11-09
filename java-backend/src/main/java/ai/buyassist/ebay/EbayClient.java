package ai.buyassist.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Thin client for the eBay Browse API.
 *
 * <p>Handles the OAuth client-credentials token (cached until shortly before
 * expiry) and the item_summary/search call. Bounded timeouts so a slow eBay
 * endpoint fails fast; the caller falls back to the mock catalog on any error.
 */
@Component
public class EbayClient {

    private static final Logger log = LoggerFactory.getLogger(EbayClient.class);
    private static final String SCOPE = "https://api.ebay.com/oauth/api_scope";

    private final EbayProperties props;
    private final RestClient oauth;
    private final RestClient browse;
    private final ObjectMapper mapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public EbayClient(EbayProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.oauth = builder.clone().baseUrl(props.oauthUrl()).requestFactory(factory).build();
        this.browse = RestClient.builder().baseUrl(props.browseUrl()).requestFactory(factory).build();
    }

    /** Returns raw itemSummaries JSON for a keyword search, or null on failure. */
    public JsonNode search(String query, int limit, Double maxPrice) {
        try {
            String token = token();
            String filter = maxPrice != null && maxPrice > 0
                    ? "price:[..%.2f],priceCurrency:USD,buyingOptions:{FIXED_PRICE}".formatted(maxPrice)
                    : "buyingOptions:{FIXED_PRICE}";
            String uri = UriComponentsBuilder.fromPath("/item_summary/search")
                    .queryParam("q", query)
                    .queryParam("limit", Math.min(Math.max(limit, 1), 50))
                    .queryParam("filter", filter)
                    .build().toUriString();

            String body = browse.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .header("X-EBAY-C-MARKETPLACE-ID", props.marketplaceId())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(body);
        } catch (Exception e) {
            log.warn("eBay search failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    /** Obtain (and cache) an application access token via client-credentials. */
    private synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        String basic = Base64.getEncoder().encodeToString(
                (props.clientId() + ":" + props.clientSecret()).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials&scope="
                + java.net.URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

        String resp = oauth.post()
                .uri("/identity/v1/oauth2/token")
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = mapper.readTree(resp);
            cachedToken = node.path("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            // Refresh a minute early.
            tokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            return cachedToken;
        } catch (Exception e) {
            throw new RuntimeException("eBay token parse failed: " + e.getMessage(), e);
        }
    }
}

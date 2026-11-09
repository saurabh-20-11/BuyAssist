package ai.buyassist.ebay;

import ai.buyassist.merchant.Product;
import ai.buyassist.merchant.ProductCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads live monitor listings from the eBay Browse API into the shared
 * {@link ProductCatalog}, so the rest of the pipeline (scoring, policy gate,
 * authorization re-resolution by id) works unchanged against real data.
 *
 * <p>Active only when {@code catalog.source=ebay} AND eBay credentials are set.
 * On any failure it logs and leaves the seeded mock catalog in place — the demo
 * never breaks if eBay is slow, rate-limited, or misconfigured.
 *
 * <p>eBay does not expose warranty, delivery estimate, or a normalized feature
 * list in Browse summaries, so those are inferred conservatively from the title.
 * Price and title are real; this keeps the deterministic engine honest while
 * using genuine live listings. Each item gets a stable id {@code ebay-<itemId>}.
 */
@Service
@ConditionalOnProperty(name = "catalog.source", havingValue = "ebay")
public class EbayCatalogService {

    private static final Logger log = LoggerFactory.getLogger(EbayCatalogService.class);

    private final EbayClient client;
    private final EbayProperties props;
    private final ProductCatalog catalog;

    public EbayCatalogService(EbayClient client, EbayProperties props, ProductCatalog catalog) {
        this.client = client;
        this.props = props;
        this.catalog = catalog;
    }

    @PostConstruct
    void loadLiveListings() {
        if (!props.isConfigured()) {
            log.warn("catalog.source=ebay but eBay credentials are not set; keeping mock catalog.");
            return;
        }
        try {
            JsonNode root = client.search("27 inch usb-c monitor", 40, null);
            if (root == null || !root.has("itemSummaries")) {
                log.warn("eBay returned no itemSummaries; keeping mock catalog.");
                return;
            }
            List<Product> live = new ArrayList<>();
            for (JsonNode item : root.get("itemSummaries")) {
                Product p = toProduct(item);
                if (p != null) {
                    live.add(p);
                }
            }
            if (live.isEmpty()) {
                log.warn("eBay returned 0 usable listings; keeping mock catalog.");
                return;
            }
            live.forEach(catalog::save);
            log.info("Loaded {} live eBay monitor listings into the catalog.", live.size());
        } catch (Exception e) {
            log.error("eBay catalog load failed; keeping mock catalog: {}", e.getMessage());
        }
    }

    private Product toProduct(JsonNode item) {
        try {
            String itemId = item.path("itemId").asText("");
            String title = item.path("title").asText("");
            JsonNode priceNode = item.path("price");
            double price = priceNode.path("value").asDouble(0);
            if (itemId.isBlank() || title.isBlank() || price <= 0) {
                return null;
            }
            String merchant = item.path("seller").path("username").asText("eBay Seller");
            String lower = title.toLowerCase(Locale.ROOT);

            // Conservative inference from the title (eBay summaries lack these fields).
            List<String> features = new ArrayList<>();
            if (lower.contains("usb-c") || lower.contains("usb c") || lower.contains("type-c")) features.add("USB-C");
            if (lower.contains("hdmi")) features.add("HDMI");
            if (lower.contains("4k")) features.add("4K");
            if (lower.contains("ips")) features.add("IPS");
            if (lower.contains("displayport") || lower.contains(" dp ")) features.add("DP");

            int size = 27;
            var m = java.util.regex.Pattern.compile("(\\d{2})\\s*(?:inch|\"|in\\b)").matcher(lower);
            if (m.find()) {
                try { size = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignore) { }
            }

            // Unknown-but-reasonable defaults for fields eBay doesn't provide.
            int warrantyYears = 1;      // conservative: assume minimal unless stated
            int deliveryDays = 5;
            double reliability = 0.85;
            double rating = 4.2;        // neutral-positive placeholder; real reviews need a reviews API
            int reviewCount = 0;        // 0 => treated as neutral by the scoring engine
            String reviewSummary = "Live eBay listing; review data not available via Browse API.";

            String brand = title.split("\\s+")[0];
            return new Product("ebay-" + itemId, merchant, brand, title, "monitor",
                    size, price, features, warrantyYears, deliveryDays, reliability,
                    rating, reviewCount, reviewSummary);
        } catch (Exception e) {
            return null;
        }
    }
}

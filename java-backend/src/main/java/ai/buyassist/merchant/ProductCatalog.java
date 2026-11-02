package ai.buyassist.merchant;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock product catalog seeded with monitors across three simulated
 * merchants: OfficeHub, TechMart, BusinessDirect. Prices are mutable so the
 * autonomous-purchasing demo can drop a price below a threshold at runtime.
 */
@Repository
public class ProductCatalog {

    private final ConcurrentHashMap<String, Product> products = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        List<Product> seed = List.of(
                // --- TechMart ---
                new Product("tm-dell-27u", "TechMart", "Dell", "Dell 27\" USB-C Monitor",
                        "monitor", 27, 219.0, List.of("USB-C", "HDMI", "IPS"), 3, 2, 0.95),
                new Product("tm-lg-27", "TechMart", "LG", "LG 27\" UltraFine",
                        "monitor", 27, 249.0, List.of("USB-C", "HDMI", "IPS"), 2, 2, 0.92),
                new Product("tm-samsung-27", "TechMart", "Samsung", "Samsung 27\" ViewFinity",
                        "monitor", 27, 199.0, List.of("HDMI", "DP"), 2, 3, 0.90),
                new Product("tm-aoc-24", "TechMart", "AOC", "AOC 24\" Value",
                        "monitor", 24, 129.0, List.of("HDMI"), 1, 2, 0.82),
                new Product("tm-dell-32", "TechMart", "Dell", "Dell 32\" 4K USB-C",
                        "monitor", 32, 389.0, List.of("USB-C", "HDMI", "DP", "4K"), 3, 4, 0.95),
                new Product("tm-asus-27", "TechMart", "Asus", "Asus 27\" ProArt",
                        "monitor", 27, 299.0, List.of("USB-C", "HDMI", "DP"), 3, 5, 0.93),
                new Product("tm-benq-27", "TechMart", "BenQ", "BenQ 27\" Designer",
                        "monitor", 27, 279.0, List.of("USB-C", "HDMI"), 3, 4, 0.91),
                new Product("tm-hp-27", "TechMart", "HP", "HP 27\" E-Series",
                        "monitor", 27, 234.0, List.of("USB-C", "DP"), 3, 3, 0.90),

                // --- OfficeHub ---
                new Product("oh-hp-27u", "OfficeHub", "HP", "HP 27\" USB-C Business",
                        "monitor", 27, 229.0, List.of("USB-C", "HDMI"), 3, 3, 0.90),
                new Product("oh-dell-27", "OfficeHub", "Dell", "Dell 27\" P-Series",
                        "monitor", 27, 239.0, List.of("USB-C", "HDMI", "DP"), 3, 3, 0.94),
                new Product("oh-lenovo-27", "OfficeHub", "Lenovo", "Lenovo 27\" ThinkVision",
                        "monitor", 27, 215.0, List.of("USB-C", "HDMI"), 3, 4, 0.89),
                new Product("oh-viewsonic-27", "OfficeHub", "ViewSonic", "ViewSonic 27\" Office",
                        "monitor", 27, 189.0, List.of("HDMI", "DP"), 2, 3, 0.85),
                new Product("oh-aoc-27", "OfficeHub", "AOC", "AOC 27\" Essential",
                        "monitor", 27, 169.0, List.of("HDMI"), 1, 2, 0.83),
                new Product("oh-philips-24", "OfficeHub", "Philips", "Philips 24\" Compact",
                        "monitor", 24, 139.0, List.of("HDMI", "USB-C"), 2, 2, 0.86),
                new Product("oh-samsung-32", "OfficeHub", "Samsung", "Samsung 32\" 4K",
                        "monitor", 32, 359.0, List.of("USB-C", "HDMI", "4K"), 2, 5, 0.90),
                new Product("oh-lg-27u", "OfficeHub", "LG", "LG 27\" USB-C Ergo",
                        "monitor", 27, 259.0, List.of("USB-C", "HDMI", "DP"), 3, 4, 0.92),

                // --- BusinessDirect ---
                new Product("bd-lenovo-27", "BusinessDirect", "Lenovo", "Lenovo 27\" Budget",
                        "monitor", 27, 205.0, List.of("USB-C", "HDMI"), 1, 1, 0.88),
                new Product("bd-dell-27u", "BusinessDirect", "Dell", "Dell 27\" USB-C Pro",
                        "monitor", 27, 225.0, List.of("USB-C", "HDMI", "DP"), 3, 2, 0.95),
                new Product("bd-hp-27", "BusinessDirect", "HP", "HP 27\" ProDisplay",
                        "monitor", 27, 221.0, List.of("USB-C", "HDMI"), 3, 3, 0.90),
                new Product("bd-asus-27", "BusinessDirect", "Asus", "Asus 27\" Commercial",
                        "monitor", 27, 243.0, List.of("USB-C", "DP"), 3, 3, 0.91),
                new Product("bd-acer-27", "BusinessDirect", "Acer", "Acer 27\" Vero",
                        "monitor", 27, 179.0, List.of("HDMI", "DP"), 2, 2, 0.84),
                new Product("bd-samsung-27u", "BusinessDirect", "Samsung", "Samsung 27\" USB-C",
                        "monitor", 27, 211.0, List.of("USB-C", "HDMI"), 2, 3, 0.90),
                new Product("bd-dell-24", "BusinessDirect", "Dell", "Dell 24\" Standard",
                        "monitor", 24, 149.0, List.of("HDMI", "DP"), 3, 2, 0.94),
                new Product("bd-lg-32", "BusinessDirect", "LG", "LG 32\" 4K USB-C",
                        "monitor", 32, 379.0, List.of("USB-C", "HDMI", "4K"), 3, 4, 0.92));

        seed.forEach(p -> products.put(p.id(), p));
    }

    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    public List<Product> findByCategory(String category) {
        return products.values().stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .toList();
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(products.get(id));
    }

    /** Overwrite a product (used by the pricing simulator to change prices). */
    public void save(Product product) {
        products.put(product.id(), product);
    }
}

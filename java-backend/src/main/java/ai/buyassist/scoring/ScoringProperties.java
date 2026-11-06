package ai.buyassist.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Weights for the deterministic quantitative scoring model. Must sum to 1.0.
 * Bound from {@code scoring.weights.*} in application.yml.
 */
@ConfigurationProperties(prefix = "scoring.weights")
public record ScoringProperties(
        double price,
        double featureMatch,
        double warranty,
        double delivery,
        double reliability,
        double reviews
) {
}

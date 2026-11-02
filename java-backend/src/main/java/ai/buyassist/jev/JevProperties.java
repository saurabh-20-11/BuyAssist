package ai.buyassist.jev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Jev (TypeSafe AI) decision layer, bound from {@code jev.*}.
 *
 * <p>The API key is read from the environment and stays server-side only; it is
 * never exposed to the Android client. The confidence/review thresholds are owned
 * here by the application, not by the model.
 */
@ConfigurationProperties(prefix = "jev")
public record JevProperties(
        String baseUrl,
        String apiKey,
        String model,
        Thresholds thresholds
) {
    public record Thresholds(
            double bestMatchConfidence,
            int matchQualityMinIndex,
            double needsClarification,
            double needsHumanReview
    ) {
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

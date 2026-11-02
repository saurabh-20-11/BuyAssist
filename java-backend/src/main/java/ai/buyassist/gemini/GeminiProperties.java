package ai.buyassist.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Gemini (Google Gen AI) integration, bound from {@code gemini.*}.
 *
 * <p>The API key is read from the environment and stays server-side only; it is
 * never exposed to the Android client. Gemini serves two roles: the
 * conversational/voice layer (later phases) and, when selected, the bounded
 * decision layer via structured-output.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String baseUrl,
        String apiKey,
        String model
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

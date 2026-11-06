package ai.buyassist.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a free-text / spoken procurement request into a structured intent using
 * Gemini constrained to JSON output.
 *
 * <p>This is the "understand" layer: Gemini extracts fields (product, quantity,
 * budget, size, required features, warranty, notes) from messy natural language.
 * It does NOT make decisions, enforce rules, or do arithmetic — downstream
 * deterministic code owns all of that. If Gemini is unavailable, callers fall
 * back to whatever structured defaults they already have.
 */
@Service
public class IntentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);
    private static final int MAX_RETRIES = 2;

    private final GeminiProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public IntentExtractionService(GeminiProperties props,
                                   RestClient.Builder builder,
                                   ObjectMapper mapper) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restClient = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
        this.mapper = mapper;
    }

    /** Structured extraction result + a short spoken-style confirmation line. */
    public record ExtractedIntent(
            String product,
            int quantity,
            double budget,
            int sizeInches,
            List<String> requiredFeatures,
            int minimumWarrantyYears,
            String notes,
            String confirmation,
            boolean available
    ) {
        public static ExtractedIntent unavailable(String transcript) {
            return new ExtractedIntent("27 inch monitor", 1, 0, 0, List.of(), 0,
                    transcript, "Intent extraction unavailable; please use the form.", false);
        }
    }

    public ExtractedIntent extract(String transcript) {
        if (!props.isConfigured() || transcript == null || transcript.isBlank()) {
            return ExtractedIntent.unavailable(transcript);
        }

        Map<String, Object> body = buildRequestBody(transcript);
        String uri = "/v1beta/models/" + props.model() + ":generateContent?key=" + props.apiKey();

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                JevLike result = restClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((req, res) -> {
                            int status = res.getStatusCode().value();
                            byte[] raw = res.getBody().readAllBytes();
                            String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                            if (status >= 400) {
                                throw new GeminiHttpException(status, text);
                            }
                            return new JevLike(mapper.readTree(text));
                        });
                return map(result.node, transcript);
            } catch (Exception e) {
                if (isTransient(e) && attempt <= MAX_RETRIES) {
                    sleep((long) (Math.pow(2, attempt) * 400));
                    continue;
                }
                log.error("Intent extraction failed (attempt {}): {}", attempt, e.getMessage());
                return ExtractedIntent.unavailable(transcript);
            }
        }
    }

    private Map<String, Object> buildRequestBody(String transcript) {
        String prompt = """
                Extract structured procurement constraints from the user's request.
                Return ONLY the typed fields. Do not invent values the user did not
                state: if a field is unspecified, use the given defaults
                (quantity=1, budget=0 meaning unspecified, sizeInches=0 meaning any,
                requiredFeatures=[], minimumWarrantyYears=0). Recognize feature words
                like USB-C, HDMI, DP, 4K, IPS. Also return a one-sentence spoken
                confirmation of your understanding, phrased for text-to-speech.

                USER REQUEST:
                %s
                """.formatted(transcript);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("product", Map.of("type", "STRING"));
        p.put("quantity", Map.of("type", "INTEGER"));
        p.put("budget", Map.of("type", "NUMBER"));
        p.put("sizeInches", Map.of("type", "INTEGER"));
        p.put("requiredFeatures", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        p.put("minimumWarrantyYears", Map.of("type", "INTEGER"));
        p.put("notes", Map.of("type", "STRING"));
        p.put("confirmation", Map.of("type", "STRING"));
        schema.put("properties", p);
        schema.put("required", List.of("product", "quantity", "budget", "sizeInches",
                "requiredFeatures", "minimumWarrantyYears", "confirmation"));

        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("responseMimeType", "application/json");
        gen.put("responseSchema", schema);
        gen.put("temperature", 0.1);

        Map<String, Object> content = Map.of("role", "user", "parts", List.of(Map.of("text", prompt)));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("contents", List.of(content));
        req.put("generationConfig", gen);
        return req;
    }

    private ExtractedIntent map(JsonNode response, String transcript) {
        JsonNode text = response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            return ExtractedIntent.unavailable(transcript);
        }
        try {
            JsonNode d = mapper.readTree(text.asText());
            List<String> features = new ArrayList<>();
            d.path("requiredFeatures").forEach(n -> features.add(n.asText()));
            ExtractedIntent ei = new ExtractedIntent(
                    d.path("product").asText("27 inch monitor"),
                    Math.max(1, d.path("quantity").asInt(1)),
                    Math.max(0, d.path("budget").asDouble(0)),
                    Math.max(0, d.path("sizeInches").asInt(0)),
                    features,
                    Math.max(0, d.path("minimumWarrantyYears").asInt(0)),
                    d.path("notes").asText(""),
                    d.path("confirmation").asText(""),
                    true);
            log.info("Extracted intent: product={} qty={} budget={} size={} features={} warranty={}",
                    ei.product(), ei.quantity(), ei.budget(), ei.sizeInches(),
                    ei.requiredFeatures(), ei.minimumWarrantyYears());
            return ei;
        } catch (Exception e) {
            log.error("Could not parse extracted intent JSON: {}", e.getMessage());
            return ExtractedIntent.unavailable(transcript);
        }
    }

    private boolean isTransient(Exception e) {
        if (e instanceof GeminiHttpException g) {
            return g.status() == 429 || (g.status() >= 500 && g.status() <= 504);
        }
        String m = String.valueOf(e.getMessage()).toLowerCase();
        return m.contains("timeout") || m.contains("timed out") || m.contains("unavailable")
                || m.contains("503") || m.contains("500") || m.contains("502") || m.contains("504");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Small wrapper so the exchange() lambda can return a parsed node. */
    private record JevLike(JsonNode node) {
    }

    private static final class GeminiHttpException extends RuntimeException {
        private final int status;

        GeminiHttpException(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}

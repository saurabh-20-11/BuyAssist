package ai.buyassist.gemini;

import ai.buyassist.decision.DecisionProvider;
import ai.buyassist.jev.JevDecisionRequest;
import ai.buyassist.jev.JevDecisionResult;
import ai.buyassist.procurement.Candidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini-backed implementation of the bounded {@link DecisionProvider}.
 *
 * <p>This is the default, zero-cost decision layer. It constrains Gemini to
 * <em>structured JSON output</em> (responseMimeType + responseSchema) so the
 * model must return exactly the four bounded answers — not prose. We deliberately
 * only ask Gemini for bounded, semantic judgments; it is never asked to do
 * arithmetic (budget/quantity/warranty live in the deterministic PolicyEngine)
 * and it never authorizes payment.
 *
 * <p>Active when {@code decision.provider=gemini} (the default). The Jev
 * implementation can be swapped in via {@code decision.provider=jev} with no
 * change to anything downstream, because both return a {@link JevDecisionResult}.
 *
 * <p>Note: Gemini does not emit Jev-style calibrated probabilities. We ask it for
 * a self-reported confidence per judgment and synthesize a two-point probability
 * distribution so the Decision Trace and thresholds have consistent inputs. This
 * difference is documented so the demo story stays honest.
 */
@Service
@ConditionalOnProperty(name = "decision.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiDecisionProvider implements DecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiDecisionProvider.class);

    private static final List<String> QUALITY_LEVELS =
            List.of("poor", "acceptable", "good", "excellent");
    private static final Map<String, String> QUALITY_LEGEND = Map.of(
            "0", "poor", "1", "acceptable", "2", "good", "3", "excellent");

    private static final int MAX_RETRIES = 2;

    private final GeminiProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public GeminiDecisionProvider(GeminiProperties props,
                                  RestClient.Builder builder,
                                  ObjectMapper mapper) {
        this.props = props;
        // Bounded connect/read timeouts so a slow or unavailable Gemini endpoint
        // fails fast into the graceful fallback instead of hanging the request.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return "gemini:" + props.model();
    }

    @Override
    public JevDecisionResult decide(JevDecisionRequest request) {
        if (!props.isConfigured() || request.policyValidCandidates().isEmpty()) {
            return JevDecisionResult.unavailable();
        }

        List<String> candidateNames = request.policyValidCandidates().stream()
                .map(Candidate::name).toList();

        String prompt = buildPrompt(request, candidateNames);
        Map<String, Object> body = buildRequestBody(prompt, candidateNames);
        String uri = "/v1beta/models/" + props.model() + ":generateContent?key=" + props.apiKey();

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                // Use exchange() to read the raw response stream directly, bypassing
                // content-type-based converter negotiation. Gemini sometimes labels
                // bodies as application/octet-stream, which trips the standard
                // String/JSON converters; reading the stream ourselves avoids that.
                JevDecisionResult result = restClient.post()
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
                            try {
                                JsonNode response = mapper.readTree(text);
                                return mapResult(extractJson(response), candidateNames);
                            } catch (Exception parse) {
                                throw new IllegalStateException(
                                        "Unparseable Gemini body: " + parse.getMessage(), parse);
                            }
                        });
                return result;
            } catch (Exception e) {
                boolean retryable = isTransient(e) && attempt <= MAX_RETRIES;
                if (retryable) {
                    long backoffMs = (long) (Math.pow(2, attempt) * 400);
                    log.warn("Gemini transient failure (attempt {}/{}): {} — retrying in {}ms",
                            attempt, MAX_RETRIES, e.getMessage(), backoffMs);
                    sleep(backoffMs);
                    continue;
                }
                log.error("Gemini decision call failed (attempt {}): {}", attempt, e.getMessage());
                return JevDecisionResult.unavailable();
            }
        }
    }

    /** Treat 5xx/429 and timeouts as transient and worth a short retry. */
    private boolean isTransient(Exception e) {
        if (e instanceof GeminiHttpException g) {
            return g.status() == 429 || (g.status() >= 500 && g.status() <= 504);
        }
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("503") || msg.contains("500") || msg.contains("502")
                || msg.contains("504") || msg.contains("429")
                || msg.contains("timed out") || msg.contains("timeout")
                || msg.contains("unavailable");
    }

    /** Carries an HTTP error status + body from inside the exchange() callback. */
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

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildPrompt(JevDecisionRequest request, List<String> candidateNames) {
        String stateJson;
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            Map<String, Object> intent = new LinkedHashMap<>();
            intent.put("product", request.intent().product());
            intent.put("quantity", request.intent().quantity());
            intent.put("budget", request.intent().budget());
            intent.put("required_features", request.intent().requiredFeatures());
            intent.put("minimum_warranty_years", request.intent().minimumWarrantyYears());
            intent.put("notes", request.intent().notes());
            state.put("intent", intent);
            state.put("candidates", request.policyValidCandidates().stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", c.name());
                m.put("merchant", c.merchant());
                m.put("total_price", c.totalPrice());
                m.put("warranty_years", c.warrantyYears());
                m.put("delivery_days", c.deliveryDays());
                m.put("features", c.features());
                m.put("customer_rating", c.rating());
                m.put("review_count", c.reviewCount());
                m.put("review_summary", c.reviewSummary());
                return m;
            }).toList());
            stateJson = mapper.writeValueAsString(state);
        } catch (Exception e) {
            stateJson = "{}";
        }

        return """
                You are a bounded decision component inside a procurement system.
                You do NOT authorize payments and you do NOT enforce hard rules
                (budget, quantity, warranty are already validated in code). Only
                the candidates listed have already passed every hard constraint.

                Given the procurement state below, answer these bounded questions
                about SEMANTIC fit with the user's stated preferences and notes:
                  - best_match: which candidate name best matches the user's intent
                    (must be one of %s, or "none").
                  - best_match_confidence: your confidence 0.0-1.0 in that choice.
                  - match_quality: one of poor, acceptable, good, excellent.
                  - match_quality_confidence: your confidence 0.0-1.0 in the rating.
                  - needs_clarification: probability 0.0-1.0 that the request is
                    ambiguous enough to ask the user a clarifying question first.
                  - needs_human_review: probability 0.0-1.0 that this purchase
                    warrants extra human review beyond normal authorization.

                When judging best_match and match_quality, weigh the customer
                review signal (customer_rating, review_count, review_summary) as
                part of semantic fit — e.g. strong reviews reinforce a good match,
                while a summary noting a relevant defect lowers it. Do not perform
                arithmetic or recompute averages; judge only semantic alignment.

                PROCUREMENT STATE:
                %s
                """.formatted(candidateNames, stateJson);
    }

    private Map<String, Object> buildRequestBody(String prompt, List<String> candidateNames) {
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("role", "user", "parts", List.of(part));

        // Response schema forcing the typed decision shape.
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("best_match", Map.of("type", "STRING"));
        properties.put("best_match_confidence", Map.of("type", "NUMBER"));
        properties.put("match_quality", Map.of(
                "type", "STRING", "enum", QUALITY_LEVELS));
        properties.put("match_quality_confidence", Map.of("type", "NUMBER"));
        properties.put("needs_clarification", Map.of("type", "NUMBER"));
        properties.put("needs_human_review", Map.of("type", "NUMBER"));
        schema.put("properties", properties);
        schema.put("required", List.of(
                "best_match", "best_match_confidence", "match_quality",
                "match_quality_confidence", "needs_clarification", "needs_human_review"));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", schema);
        generationConfig.put("temperature", 0.2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        return body;
    }

    /** Pull the model's JSON text out of candidates[0].content.parts[0].text and parse it. */
    private JsonNode extractJson(JsonNode response) throws Exception {
        JsonNode text = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no structured text");
        }
        return mapper.readTree(text.asText());
    }

    private JevDecisionResult mapResult(JsonNode d, List<String> candidateNames) {
        String bestMatch = d.path("best_match").asText("none");
        double bestConf = clamp01(d.path("best_match_confidence").asDouble(0.0));
        String quality = d.path("match_quality").asText("poor");
        double qualityConf = clamp01(d.path("match_quality_confidence").asDouble(0.0));
        double needsClar = clamp01(d.path("needs_clarification").asDouble(0.0));
        double needsReview = clamp01(d.path("needs_human_review").asDouble(0.0));

        int qualityIndex = QUALITY_LEVELS.indexOf(quality.toLowerCase());
        if (qualityIndex < 0) {
            qualityIndex = 0;
        }

        // Synthesize a probability distribution over the Choice options from the
        // self-reported confidence: chosen option gets `bestConf`, the remaining
        // mass is spread across the other options. Keeps downstream inputs uniform
        // with the Jev provider.
        Map<String, Double> probs = new LinkedHashMap<>();
        int others = Math.max(1, candidateNames.size());
        double remainder = others > 0 ? (1.0 - bestConf) / Math.max(1, candidateNames.size()) : 0.0;
        for (String name : candidateNames) {
            probs.put(name, name.equals(bestMatch) ? bestConf : remainder);
        }
        if (!probs.containsKey(bestMatch)) {
            probs.put(bestMatch, bestConf);
        }

        JevDecisionResult result = new JevDecisionResult(
                bestMatch, bestConf, probs,
                qualityIndex, QUALITY_LEGEND, qualityConf,
                needsClar, needsReview,
                providerName(), 0L);

        log.info("Gemini decision best={} conf={} quality={} clarify={} review={}",
                bestMatch, bestConf, quality, needsClar, needsReview);
        return result;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

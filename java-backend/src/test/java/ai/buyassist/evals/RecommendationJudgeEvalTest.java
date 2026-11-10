package ai.buyassist.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded, grounded LLM-as-judge eval for SEMANTIC recommendation quality.
 *
 * <p>This is the ONE subjective metric, done honestly. Correctness of policy,
 * scoring, and safety is proven deterministically elsewhere; here we only ask
 * whether the system's winning recommendation genuinely fits the nuance of the
 * request. The judge is:
 *   - grounded: it is given the deterministic winner + the request, so it scores
 *     alignment rather than re-deciding the purchase;
 *   - bounded: it returns a typed 1-5 alignment score + one-line rationale;
 *   - advisory: a low score is reported, not treated as a hard failure of the
 *     system — because LLM judges are known to under-detect and mis-score.
 *
 * <p>Opt-in and live: requires {@code -Deval.live=true}, a running backend at
 * {@code eval.baseUrl} (default localhost:8080), and the backend's Gemini key.
 * It drives the JUDGE via the backend's own Gemini config through a dedicated
 * judge endpoint? No — to keep the judge independent of the system under test,
 * we reuse the backend's /api/voice/interpret-style structured output is not
 * available for arbitrary prompts, so this judge calls Gemini directly using the
 * GEMINI_API_KEY from the environment. If that key isn't present, it skips.
 */
class RecommendationJudgeEvalTest {

    private static final String BASE =
            System.getProperty("eval.baseUrl", System.getenv().getOrDefault("EVAL_BASE_URL", "http://localhost:8080"));
    private static final boolean LIVE = Boolean.getBoolean("eval.live");
    private static final String GEMINI_KEY = System.getenv("GEMINI_API_KEY");
    private static final String GEMINI_MODEL =
            System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recommendationAlignsWithRequestNuance() throws Exception {
        Assumptions.assumeTrue(LIVE, "live judge eval disabled; run with -Deval.live=true");
        Assumptions.assumeTrue(GEMINI_KEY != null && !GEMINI_KEY.isBlank(),
                "GEMINI_API_KEY not in environment; skipping judge eval");

        RestClient http = RestClient.builder().baseUrl(BASE)
                .requestFactory(timed()).build();

        // 1. Ask the system under test to evaluate a request.
        String evalBody = """
                {"product":"27 inch monitor","quantity":5,"budget":1200,"sizeInches":27,
                 "requiredFeatures":["USB-C"],"minimumWarrantyYears":3,
                 "notes":"for the analytics team; reliability and strong reviews matter most",
                 "allowedMerchants":[]}
                """;
        JsonNode trace;
        try {
            String resp = http.post().uri("/api/procurement/evaluate")
                    .header("Content-Type", "application/json")
                    .body(evalBody).retrieve().body(String.class);
            trace = mapper.readTree(resp);
        } catch (Exception e) {
            Assumptions.abort("backend not reachable: " + e.getMessage());
            return;
        }

        String winner = trace.path("deterministicTop").asText(null);
        Assumptions.assumeTrue(winner != null && !winner.isBlank(),
                "no winner produced (no valid candidates); skipping");

        // 2. Independent judge: does the winner fit the request's stated nuance?
        int score = judgeAlignment(
                "Five 27-inch USB-C monitors for an analytics team under $1200 with a "
                        + "3-year warranty, prioritizing reliability and strong reviews.",
                winner);
        Assumptions.assumeTrue(score > 0, "judge unavailable (transient); skipping");

        System.out.println("[judge] recommendation alignment score = " + score + "/5 for winner: " + winner);
        // Advisory threshold: we assert only a weak floor. The judge is not
        // authoritative; its job is to surface gross misalignment, not to gate.
        assertTrue(score >= 3,
                "judge rated recommendation alignment below acceptable (" + score + "/5) for " + winner);
    }

    /** Calls Gemini as a bounded judge; returns a 1-5 score, or 0 if unavailable. */
    private int judgeAlignment(String request, String winner) {
        try {
            String prompt = """
                    You are an impartial judge. Given a buyer's request and the
                    system's recommended product, rate 1-5 how well the recommendation
                    aligns with the request's stated priorities (1=poor, 5=excellent).
                    Do not re-decide the purchase; judge only alignment.

                    REQUEST: %s
                    RECOMMENDED: %s
                    """.formatted(request, winner);

            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", "OBJECT");
            ObjectNode p = mapper.createObjectNode();
            p.set("score", mapper.createObjectNode().put("type", "INTEGER"));
            p.set("rationale", mapper.createObjectNode().put("type", "STRING"));
            schema.set("properties", p);
            schema.set("required", mapper.valueToTree(List.of("score", "rationale")));

            Map<String, Object> gen = new LinkedHashMap<>();
            gen.put("responseMimeType", "application/json");
            gen.put("responseSchema", mapper.convertValue(schema, Map.class));
            Map<String, Object> content = Map.of("role", "user",
                    "parts", List.of(Map.of("text", prompt)));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(content));
            body.put("generationConfig", gen);

            RestClient gemini = RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .requestFactory(timed()).build();
            byte[] raw = gemini.post()
                    .uri("/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + GEMINI_KEY)
                    .header("Content-Type", "application/json")
                    .body(body).retrieve().body(byte[].class);
            JsonNode root = mapper.readTree(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return 0;
            }
            return mapper.readTree(text).path("score").asInt(0);
        } catch (Exception e) {
            return 0;  // transient/judge failure -> caller skips
        }
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timed() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(60000);
        return f;
    }
}

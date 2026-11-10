package ai.buyassist.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live eval: the Gemini "understand" layer (speech/NL -> structured intent).
 *
 * <p>Unlike the deterministic evals, this exercises a real model, so it runs
 * against a RUNNING backend at {@code EVAL_BASE_URL} (default http://localhost:8080)
 * and is OPT-IN: set {@code -Deval.live=true}. It grades numeric fields exactly
 * and features as a case-insensitive superset, and it SKIPS gracefully (JUnit
 * assumption) when the backend or model is unavailable — a free-tier 503 must
 * never show up as a red test. This measures the understand layer honestly
 * without making the default build depend on a flaky external API.
 */
class IntentExtractionEvalTest {

    private static final String BASE =
            System.getProperty("eval.baseUrl", System.getenv().getOrDefault("EVAL_BASE_URL", "http://localhost:8080"));
    private static final boolean LIVE = Boolean.getBoolean("eval.live");

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http = RestClient.builder()
            .baseUrl(BASE)
            .requestFactory(timedFactory())
            .build();

    @TestFactory
    List<DynamicTest> intentCases() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/evals/intent-cases.json")) {
            root = mapper.readTree(in);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> runCase(c)));
        }
        return tests;
    }

    private void runCase(JsonNode c) {
        Assumptions.assumeTrue(LIVE,
                "live intent eval disabled; run with -Deval.live=true and the backend running");

        JsonNode result;
        try {
            String body = mapper.createObjectNode()
                    .put("transcript", c.get("transcript").asText()).toString();
            String resp = http.post().uri("/api/voice/interpret")
                    .header("Content-Type", "application/json")
                    .body(body).retrieve().body(String.class);
            result = mapper.readTree(resp);
        } catch (Exception e) {
            Assumptions.abort("backend not reachable at " + BASE + ": " + e.getMessage());
            return;
        }

        // If the model was unavailable (free-tier 503), skip rather than fail.
        Assumptions.assumeTrue(result.path("available").asBoolean(false),
                "model unavailable (transient); skipping");

        JsonNode e = c.get("expect");
        if (e.has("quantity")) {
            assertEquals(e.get("quantity").asInt(), result.path("quantity").asInt(),
                    "quantity mismatch");
        }
        if (e.has("budget")) {
            assertEquals(e.get("budget").asDouble(), result.path("budget").asDouble(), 0.01,
                    "budget mismatch");
        }
        if (e.has("sizeInches")) {
            assertEquals(e.get("sizeInches").asInt(), result.path("sizeInches").asInt(),
                    "sizeInches mismatch");
        }
        if (e.has("minimumWarrantyYears")) {
            assertEquals(e.get("minimumWarrantyYears").asInt(),
                    result.path("minimumWarrantyYears").asInt(), "warranty mismatch");
        }
        if (e.has("requiredFeatures")) {
            List<String> expected = new ArrayList<>();
            e.get("requiredFeatures").forEach(n -> expected.add(n.asText().toLowerCase(Locale.ROOT)));
            List<String> actual = new ArrayList<>();
            result.path("requiredFeatures").forEach(n -> actual.add(n.asText().toLowerCase(Locale.ROOT)));
            assertTrue(actual.containsAll(expected),
                    "expected features " + expected + " not all present in " + actual);
        }
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timedFactory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        return f;
    }
}

package ai.buyassist.evals;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ConstraintCheck;
import ai.buyassist.procurement.PolicyEngine;
import ai.buyassist.procurement.PolicyEvaluation;
import ai.buyassist.procurement.ProcurementIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic eval: the hard-constraint policy gate.
 *
 * <p>This is the backbone eval. Correctness here is objective — given an intent
 * and a candidate, exactly which hard constraints pass is a fact, not an opinion,
 * so NO LLM judge is involved. Each golden case in {@code evals/policy-cases.json}
 * asserts both the overall valid/invalid verdict and (for failures) which
 * specific constraints must have failed. Any mismatch is a real bug.
 */
class PolicyGateEvalTest {

    private final PolicyEngine policyEngine = new PolicyEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @TestFactory
    List<DynamicTest> policyGoldenCases() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/evals/policy-cases.json")) {
            root = mapper.readTree(in);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> runCase(c)));
        }
        return tests;
    }

    private void runCase(JsonNode c) {
        ProcurementIntent intent = intentOf(c.get("intent"));
        Candidate candidate = candidateOf(c.get("candidate"));

        PolicyEvaluation eval = policyEngine.evaluate(intent, candidate, null);
        boolean expectValid = c.get("expectValid").asBoolean();

        assertEquals(expectValid, eval.isPolicyValid(),
                "policy-valid verdict mismatch for: " + c.get("name").asText()
                        + " | checks=" + describe(eval.checks()));

        if (!expectValid && c.has("expectFailed")) {
            List<String> expectedFailed = new ArrayList<>();
            c.get("expectFailed").forEach(n -> expectedFailed.add(n.asText()));
            List<String> actualFailed = eval.checks().stream()
                    .filter(chk -> !chk.passed())
                    .map(ConstraintCheck::name)
                    .collect(Collectors.toList());
            assertTrue(actualFailed.containsAll(expectedFailed),
                    "expected failed constraints " + expectedFailed
                            + " but got " + actualFailed);
        }
    }

    private ProcurementIntent intentOf(JsonNode n) {
        List<String> features = new ArrayList<>();
        n.get("requiredFeatures").forEach(f -> features.add(f.asText()));
        return new ProcurementIntent(
                n.get("product").asText(),
                n.get("quantity").asInt(),
                n.get("budget").asDouble(),
                features,
                n.get("minimumWarrantyYears").asInt(),
                "eval");
    }

    private Candidate candidateOf(JsonNode n) {
        List<String> features = new ArrayList<>();
        n.get("features").forEach(f -> features.add(f.asText()));
        return new Candidate(
                n.get("name").asText(),
                n.get("merchant").asText(),
                n.get("unitPrice").asDouble(),
                n.get("quantity").asInt(),
                n.get("warrantyYears").asInt(),
                n.get("deliveryDays").asInt(),
                features,
                n.get("reliability").asDouble());
    }

    private String describe(List<ConstraintCheck> checks) {
        return checks.stream()
                .map(c -> c.name() + "=" + (c.passed() ? "pass" : "FAIL"))
                .collect(Collectors.joining(", "));
    }
}

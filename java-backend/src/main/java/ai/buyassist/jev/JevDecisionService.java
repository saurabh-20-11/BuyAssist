package ai.buyassist.jev;

import ai.buyassist.decision.DecisionProvider;
import ai.buyassist.jev.wire.JevApiRequest;
import ai.buyassist.jev.wire.JevApiResponse;
import ai.buyassist.procurement.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the bounded Jev questions for a procurement decision, calls Jev, and
 * maps the typed response into a {@link JevDecisionResult}.
 *
 * <p>This service performs no gating itself — it only asks the model the four
 * bounded questions and returns typed answers with probabilities. The policy
 * engine applies the configured thresholds. If Jev is not configured, it returns
 * {@link JevDecisionResult#unavailable()} so the pipeline degrades to
 * deterministic-only behavior.
 *
 * <p>The four questions:
 * <ul>
 *   <li><b>best_match</b> (Choice): which policy-valid candidate best matches intent</li>
 *   <li><b>match_quality</b> (Score): poor/acceptable/good/excellent</li>
 *   <li><b>needs_clarification</b> (Noul): is there unresolved ambiguity?</li>
 *   <li><b>needs_human_review</b> (Noul): warrants review beyond normal AP2 step?</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "decision.provider", havingValue = "jev")
public class JevDecisionService implements DecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionService.class);

    private static final List<String> QUALITY_LEVELS =
            List.of("poor", "acceptable", "good", "excellent");

    private final JevClient client;
    private final JevProperties props;

    public JevDecisionService(JevClient client, JevProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String providerName() {
        return "jev:" + props.model();
    }

    @Override
    public JevDecisionResult decide(JevDecisionRequest request) {
        if (!props.isConfigured() || request.policyValidCandidates().isEmpty()) {
            return JevDecisionResult.unavailable();
        }

        Object state = buildState(request);
        Map<String, JevApiRequest.Question> questions = buildQuestions(request);
        JevApiRequest apiRequest = new JevApiRequest(props.model(), state, questions);

        JevApiResponse response = client.systemOne(apiRequest);
        return mapResult(response);
    }

    /** Structured state: intent plus the policy-valid candidates only. */
    private Object buildState(JevDecisionRequest request) {
        Map<String, Object> state = new LinkedHashMap<>();

        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("product", request.intent().product());
        intent.put("quantity", request.intent().quantity());
        intent.put("budget", request.intent().budget());
        intent.put("required_features", request.intent().requiredFeatures());
        intent.put("minimum_warranty_years", request.intent().minimumWarrantyYears());
        intent.put("notes", request.intent().notes());
        state.put("intent", intent);

        List<Map<String, Object>> candidates = request.policyValidCandidates().stream()
                .map(this::candidateState)
                .toList();
        state.put("candidates", candidates);

        return state;
    }

    private Map<String, Object> candidateState(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.name());
        m.put("merchant", c.merchant());
        m.put("total_price", c.totalPrice());
        m.put("warranty_years", c.warrantyYears());
        m.put("delivery_days", c.deliveryDays());
        m.put("features", c.features());
        return m;
    }

    private Map<String, JevApiRequest.Question> buildQuestions(JevDecisionRequest request) {
        // Choice options are the policy-valid candidate names plus "none".
        Map<String, String> options = new LinkedHashMap<>();
        for (Candidate c : request.policyValidCandidates()) {
            options.put(c.name(), null);
        }
        options.put("none", "No candidate is a good match for the user's intent");

        Map<String, JevApiRequest.Question> q = new LinkedHashMap<>();
        q.put("best_match", JevApiRequest.Question.choice(
                "Which candidate best matches the user's stated preferences and notes?",
                options));
        q.put("match_quality", JevApiRequest.Question.score(
                "How strongly does the best-matching candidate satisfy the user's semantic preferences?",
                QUALITY_LEVELS));
        q.put("needs_clarification", JevApiRequest.Question.noul(
                "There is unresolved ambiguity in the request that should be clarified "
                        + "with the user before purchasing"));
        q.put("needs_human_review", JevApiRequest.Question.noul(
                "This proposed purchase warrants human review beyond the normal "
                        + "authorization step"));
        return q;
    }

    private JevDecisionResult mapResult(JevApiResponse response) {
        JevApiResponse.Answer best = answer(response, "best_match");
        JevApiResponse.Answer quality = answer(response, "match_quality");
        JevApiResponse.Answer clarify = answer(response, "needs_clarification");
        JevApiResponse.Answer review = answer(response, "needs_human_review");

        long inputTokens = response.usage() != null && response.usage().inputTokens() != null
                ? response.usage().inputTokens() : 0L;

        JevDecisionResult result = new JevDecisionResult(
                best != null && best.choice() != null ? best.choice() : "none",
                best != null && best.confidence() != null ? best.confidence() : 0.0,
                best != null ? best.probabilities() : Map.of(),
                quality != null && quality.score() != null ? quality.score() : 0.0,
                quality != null ? quality.legend() : Map.of(),
                quality != null && quality.confidence() != null ? quality.confidence() : 0.0,
                clarify != null && clarify.noul() != null ? clarify.noul() : 0.0,
                review != null && review.noul() != null ? review.noul() : 0.0,
                response.model(),
                inputTokens);

        log.info("Jev decision model={} best={} conf={} quality={} clarify={} review={}",
                result.answeringModel(), result.bestMatchChoice(), result.bestMatchConfidence(),
                result.matchQualityLabel(), result.needsClarification(), result.needsHumanReview());
        return result;
    }

    private JevApiResponse.Answer answer(JevApiResponse response, String key) {
        return response.answers() == null ? null : response.answers().get(key);
    }
}

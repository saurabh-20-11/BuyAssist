package ai.buyassist.jev.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Wire response from /v1/systemone.
 *
 * <p>Each entry in {@code answers} carries a {@code type} plus the type-specific
 * fields. We model a permissive union so a single DTO covers choice/score/noul.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JevApiResponse(String model, Map<String, Answer> answers, Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
            String type,
            // choice
            String choice,
            // score
            Double score,
            Map<String, String> legend,
            // noul
            Double noul,
            // choice + score
            Double confidence,
            Map<String, Double> probabilities
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Long inputTokens,
            @JsonProperty("output_tokens") Long outputTokens) {
    }
}

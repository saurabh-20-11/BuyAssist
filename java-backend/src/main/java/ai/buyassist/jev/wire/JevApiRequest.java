package ai.buyassist.jev.wire;

import java.util.Map;

/**
 * Wire body for POST https://api.typesafe.ai/v1/systemone.
 *
 * @param model     model id, e.g. "jev-1.13.0" (pin a version; aliases move)
 * @param state     structured application state (object/string/array)
 * @param questions map of our-key -> typed question
 */
public record JevApiRequest(Object model, Object state, Map<String, Question> questions) {

    public JevApiRequest(String model, Object state, Map<String, Question> questions) {
        this((Object) model, state, questions);
    }

    /**
     * A typed question. {@code type} is "choice" | "score" | "noul".
     * For choice, {@code criteria} is a map option -> description (value may be null).
     * For score, {@code criteria} is an ordered list of level labels (low -> high).
     * For noul, {@code criteria} is optional (may be null).
     */
    public record Question(String type, String instructions, Object criteria) {

        public static Question choice(String instructions, Map<String, String> options) {
            return new Question("choice", instructions, options);
        }

        public static Question score(String instructions, java.util.List<String> levels) {
            return new Question("score", instructions, levels);
        }

        public static Question noul(String instructions) {
            return new Question("noul", instructions, null);
        }
    }
}

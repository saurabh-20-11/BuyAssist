package ai.buyassist.evals;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ProcurementIntent;
import ai.buyassist.scoring.ScoredCandidate;
import ai.buyassist.scoring.ScoringEngine;
import ai.buyassist.scoring.ScoringProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic eval: the quantitative scoring engine.
 *
 * <p>Scoring is reproducible and weight-driven, so expected rankings are facts.
 * Uses the production weights (price 30, feature 25, warranty 15, delivery 10,
 * reliability 10, reviews 10). No AI judge involved.
 */
class ScoringEvalTest {

    // Production weights mirrored from application.yml.
    private final ScoringEngine engine = new ScoringEngine(
            new ScoringProperties(0.30, 0.25, 0.15, 0.10, 0.10, 0.10));

    private ProcurementIntent intent() {
        return new ProcurementIntent("27 inch monitor", 5, 1200,
                List.of("USB-C"), 3, "eval");
    }

    @Test
    void cheaperBetterReviewedCandidateOutranksPricierWeakerOne() {
        // A: cheaper, strong reviews, fast delivery. B: pricier, weaker reviews.
        Candidate a = new Candidate("A", "M1", 200, 5, 3, 2,
                List.of("USB-C", "HDMI"), 0.95, 4.7, 2000, "great");
        Candidate b = new Candidate("B", "M2", 240, 5, 3, 5,
                List.of("USB-C"), 0.85, 4.0, 300, "ok");

        List<ScoredCandidate> ranked = engine.rank(intent(), List.of(b, a));
        assertEquals("A", ranked.get(0).candidate().name(),
                "cheaper, better-reviewed, faster candidate should rank first");
        assertTrue(ranked.get(0).total() > ranked.get(1).total(),
                "winner's total must exceed runner-up");
    }

    @Test
    void reviewComponentIsPresentAndReflectsRating() {
        Candidate highRated = new Candidate("High", "M", 220, 5, 3, 3,
                List.of("USB-C"), 0.9, 4.8, 1500, "excellent");
        Candidate lowRated = new Candidate("Low", "M", 220, 5, 3, 3,
                List.of("USB-C"), 0.9, 3.2, 400, "mixed");

        List<ScoredCandidate> ranked = engine.rank(intent(), List.of(lowRated, highRated));

        double highReview = ranked.stream().filter(s -> s.candidate().name().equals("High"))
                .findFirst().orElseThrow().components().get("reviews");
        double lowReview = ranked.stream().filter(s -> s.candidate().name().equals("Low"))
                .findFirst().orElseThrow().components().get("reviews");

        // 4.8/5 -> 96; 3.2/5 -> 64.
        assertEquals(96.0, highReview, 0.5);
        assertEquals(64.0, lowReview, 0.5);
        assertEquals("High", ranked.get(0).candidate().name(),
                "with all else equal, higher rating should win on the review dimension");
    }

    @Test
    void unknownReviewsTreatedAsNeutralNotZero() {
        // rating 0 => unknown => neutral 60, must NOT tank the candidate to 0.
        Candidate noReviews = new Candidate("NoRev", "M", 210, 5, 3, 2,
                List.of("USB-C"), 0.95);  // backward-compat ctor => rating 0
        List<ScoredCandidate> ranked = engine.rank(intent(), List.of(noReviews));
        double review = ranked.get(0).components().get("reviews");
        assertEquals(60.0, review, 0.01, "unknown reviews must map to neutral 60, not 0");
    }

    @Test
    void allComponentsInZeroToHundred() {
        Candidate c = new Candidate("C", "M", 219, 5, 3, 2,
                List.of("USB-C"), 0.95, 4.5, 1000, "good");
        var components = engine.rank(intent(), List.of(c)).get(0).components();
        components.forEach((k, v) ->
                assertTrue(v >= 0.0 && v <= 100.0, k + " out of [0,100]: " + v));
    }
}

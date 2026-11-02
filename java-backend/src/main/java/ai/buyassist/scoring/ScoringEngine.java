package ai.buyassist.scoring;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.ProcurementIntent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic quantitative scoring model.
 *
 * <p>Weighted rubric: price 35%, feature match 25%, warranty 20%, delivery 10%,
 * vendor reliability 10%. Every sub-score is in [0,100] and fully reproducible.
 * No AI model is involved here — this is one of the three independent ranking
 * signals (deterministic score + Jev semantic eval + hard policy constraints).
 */
@Service
public class ScoringEngine {

    private final ScoringProperties weights;

    public ScoringEngine(ScoringProperties weights) {
        this.weights = weights;
    }

    /**
     * Score all candidates against the intent, ranked best-first.
     * Scoring does not reject anything — the policy gate does that separately.
     */
    public List<ScoredCandidate> rank(ProcurementIntent intent, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double cheapestTotal = candidates.stream()
                .mapToDouble(Candidate::totalPrice).min().orElse(0);
        double dearestTotal = candidates.stream()
                .mapToDouble(Candidate::totalPrice).max().orElse(0);
        int longestWarranty = candidates.stream()
                .mapToInt(Candidate::warrantyYears).max().orElse(1);
        int fastestDelivery = candidates.stream()
                .mapToInt(Candidate::deliveryDays).min().orElse(0);
        int slowestDelivery = candidates.stream()
                .mapToInt(Candidate::deliveryDays).max().orElse(0);

        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            double priceScore = normalizeLowerIsBetter(c.totalPrice(), cheapestTotal, dearestTotal);
            double featureScore = featureMatch(intent, c);
            double warrantyScore = longestWarranty <= 0
                    ? 100.0 : clamp0to100(100.0 * c.warrantyYears() / longestWarranty);
            double deliveryScore = normalizeLowerIsBetter(c.deliveryDays(), fastestDelivery, slowestDelivery);
            double reliabilityScore = clamp0to100(c.reliability() * 100.0);

            double total =
                    weights.price() * priceScore
                            + weights.featureMatch() * featureScore
                            + weights.warranty() * warrantyScore
                            + weights.delivery() * deliveryScore
                            + weights.reliability() * reliabilityScore;

            Map<String, Double> components = new LinkedHashMap<>();
            components.put("price", round(priceScore));
            components.put("featureMatch", round(featureScore));
            components.put("warranty", round(warrantyScore));
            components.put("delivery", round(deliveryScore));
            components.put("reliability", round(reliabilityScore));

            scored.add(new ScoredCandidate(c, round(total), components));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::total).reversed());
        return scored;
    }

    /** Fraction of required features present, as a 0..100 score. */
    private double featureMatch(ProcurementIntent intent, Candidate c) {
        List<String> required = intent.requiredFeatures();
        if (required.isEmpty()) {
            return 100.0;
        }
        List<String> have = c.features().stream().map(String::toLowerCase).toList();
        long matched = required.stream()
                .map(String::toLowerCase)
                .filter(have::contains)
                .count();
        return clamp0to100(100.0 * matched / required.size());
    }

    /** Map a value where lower is better onto 0..100 (best=100). */
    private double normalizeLowerIsBetter(double value, double best, double worst) {
        if (worst <= best) {
            return 100.0;
        }
        double score = 100.0 * (worst - value) / (worst - best);
        return clamp0to100(score);
    }

    private double clamp0to100(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

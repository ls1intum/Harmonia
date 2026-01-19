package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.util.DtoUtils;

/**
 * Result of LLM-based effort rating for a commit chunk.
 *
 * @param effortScore  Overall effort score (1-10). Higher = more work.
 * @param complexity   Technical complexity (1-10). Higher = more complex.
 * @param novelty      Originality of the work (1-10). Low =
 *                     copy-paste/generated.
 * @param type         Classification of the commit type (FEATURE, BUG_FIX,
 *                     etc.)
 * @param confidence   LLM's confidence in the rating (0.0-1.0)
 * @param reasoning    Brief explanation of the rating
 * @param isError      Whether this rating represents an error during analysis
 * @param errorMessage The error message if analysis failed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EffortRatingDTO(
        double effortScore,
        double complexity,
        double novelty,
        CommitLabel type,
        double confidence,
        String reasoning,
        boolean isError,
        String errorMessage) {
    /**
     * Calculates a weighted effort score.
     * Formula: effortScore * (0.5 + 0.3*complexity + 0.2*novelty) / 10
     * Normalizes to roughly match raw effort score but considers quality.
     */
    public double weightedEffort() {
        return DtoUtils.calculateWeightedEffort(effortScore, complexity, novelty);
    }

    /**
     * Returns a default low-effort rating for trivial changes.
     */
    public static EffortRatingDTO trivial(String reason) {
        return new EffortRatingDTO(1.0, 1.0, 1.0, CommitLabel.TRIVIAL, 1.0, reason, false, null);
    }

    /**
     * Returns a default rating when AI is disabled.
     */
    public static EffortRatingDTO disabled() {
        return new EffortRatingDTO(5.0, 5.0, 5.0, CommitLabel.TRIVIAL, 0.0, "AI disabled", false, null);
    }

    /**
     * Returns an error rating when AI analysis fails.
     */
    public static EffortRatingDTO error(String errorMessage) {
        return new EffortRatingDTO(0.0, 0.0, 0.0, CommitLabel.TRIVIAL, 0.0, errorMessage, true, errorMessage);
    }
}

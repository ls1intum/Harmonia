package de.tum.cit.aet.analysis.dto.cqi;

/**
 * Represents a penalty applied to the CQI score.
 *
 * @param type        Penalty type identifier
 * @param multiplier  Multiplier applied to score (0.0-1.0)
 * @param reason      Human-readable explanation
 */
public record CQIPenaltyDTO(
        String type,
        double multiplier,
        String reason
) {
    // Predefined penalty types
    public static final String SOLO_DEVELOPMENT = "SOLO_DEVELOPMENT";
    public static final String SEVERE_IMBALANCE = "SEVERE_IMBALANCE";
    public static final String HIGH_TRIVIAL_RATIO = "HIGH_TRIVIAL_RATIO";
    public static final String LOW_CONFIDENCE = "LOW_CONFIDENCE";
    public static final String LATE_WORK = "LATE_WORK";

    /**
     * Create solo development penalty.
     */
    public static CQIPenaltyDTO soloDevelopment(double share) {
        return new CQIPenaltyDTO(
                SOLO_DEVELOPMENT,
                0.25,
                String.format("One contributor has %.0f%% of effort (>85%%)", share * 100)
        );
    }

    /**
     * Create severe imbalance penalty.
     */
    public static CQIPenaltyDTO severeImbalance(double share) {
        return new CQIPenaltyDTO(
                SEVERE_IMBALANCE,
                0.70,
                String.format("One contributor has %.0f%% of effort (>70%%)", share * 100)
        );
    }

    /**
     * Create high trivial ratio penalty.
     */
    public static CQIPenaltyDTO highTrivialRatio(double ratio) {
        return new CQIPenaltyDTO(
                HIGH_TRIVIAL_RATIO,
                0.85,
                String.format("%.0f%% of commits are trivial (>50%%)", ratio * 100)
        );
    }

    /**
     * Create low confidence penalty.
     */
    public static CQIPenaltyDTO lowConfidence(double ratio) {
        return new CQIPenaltyDTO(
                LOW_CONFIDENCE,
                0.90,
                String.format("%.0f%% of ratings have low confidence (>40%%)", ratio * 100)
        );
    }

    /**
     * Create late work penalty.
     */
    public static CQIPenaltyDTO lateWork(double ratio) {
        return new CQIPenaltyDTO(
                LATE_WORK,
                0.85,
                String.format("%.0f%% of work done in final 20%% of project (>50%%)", ratio * 100)
        );
    }
}

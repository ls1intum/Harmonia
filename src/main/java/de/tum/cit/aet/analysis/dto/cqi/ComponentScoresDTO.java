package de.tum.cit.aet.analysis.dto.cqi;

/**
 * Individual component scores for CQI calculation.
 *
 * @param effortBalance   Effort distribution balance (0-100)
 * @param locBalance      Lines of code distribution balance (0-100)
 * @param temporalSpread  Temporal distribution of work (0-100)
 * @param ownershipSpread File ownership distribution (0-100)
 */
public record ComponentScoresDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread
) {
    /**
     * Create zero scores.
     */
    public static ComponentScoresDTO zero() {
        return new ComponentScoresDTO(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Calculate weighted sum with given weights.
     */
    public double weightedSum(double wEffort, double wLoc, double wTemporal, double wOwnership) {
        return wEffort * effortBalance
                + wLoc * locBalance
                + wTemporal * temporalSpread
                + wOwnership * ownershipSpread;
    }

    /**
     * Get summary string for logging.
     */
    public String toSummary() {
        return String.format("Effort=%.1f, LoC=%.1f, Temporal=%.1f, Ownership=%.1f",
                effortBalance, locBalance, temporalSpread, ownershipSpread);
    }
}

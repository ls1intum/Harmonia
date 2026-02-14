package de.tum.cit.aet.analysis.dto.cqi;

/**
 * Individual component scores for CQI calculation.
 *
 * @param effortBalance   Effort distribution balance (0-100)
 * @param locBalance      Lines of code distribution balance (0-100)
 * @param temporalSpread  Temporal distribution of work (0-100)
 * @param ownershipSpread File ownership distribution (0-100)
 * @param pairProgramming   Pair programming collaboration verification (0-100, nullable)
 */
public record ComponentScoresDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread,
        Double pairProgramming
) {
    /**
     * Create zero scores.
     */
    public static ComponentScoresDTO zero() {
        return new ComponentScoresDTO(0.0, 0.0, 0.0, 0.0, null);
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
     * Calculate weighted sum with 5 components including pair programming.
     */
    public double weightedSum(double wEffort, double wLoc, double wTemporal, double wOwnership, double wPairProgramming) {
        double sum = wEffort * effortBalance
                + wLoc * locBalance
                + wTemporal * temporalSpread
                + wOwnership * ownershipSpread;
        if (pairProgramming != null) {
            sum += wPairProgramming * pairProgramming;
        }
        return sum;
    }

    /**
     * Get summary string for logging.
     */
    public String toSummary() {
        if (pairProgramming != null) {
            return String.format("Effort=%.1f, LoC=%.1f, Temporal=%.1f, Ownership=%.1f, PairProgramming=%.1f",
                    effortBalance, locBalance, temporalSpread, ownershipSpread, pairProgramming);
        }
        return String.format("Effort=%.1f, LoC=%.1f, Temporal=%.1f, Ownership=%.1f",
                effortBalance, locBalance, temporalSpread, ownershipSpread);
    }
}

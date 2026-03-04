package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.pairProgramming.enums.PairProgrammingStatus;

import java.util.List;

/**
 * Individual component scores for CQI calculation.
 *
 * @param effortBalance         effort distribution balance (0-100)
 * @param locBalance            lines of code distribution balance (0-100)
 * @param temporalSpread        temporal distribution of work (0-100)
 * @param ownershipSpread       file ownership distribution (0-100)
 * @param pairProgramming       pair programming collaboration score (0-100, nullable)
 * @param pairProgrammingStatus Status of pair programming metric:
 *                              PASS/FAIL (team found and attendance threshold passed/failed),
 *                              NOT_FOUND (Excel uploaded but team missing),
 *                              WARNING (cancelled sessions affected evaluation),
 *                              null if no Excel uploaded.
 * @param pairProgrammingStatus pair programming metric status: "FOUND", "NOT_FOUND",
 *                              "WARNING", or null if no attendance data uploaded
 * @param dailyDistribution     daily lines of code changed (added + deleted) per day from prefiltered commits,
 *                              used for temporal spread bar chart visualization (nullable)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComponentScoresDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread,
        Double pairProgramming,
        PairProgrammingStatus pairProgrammingStatus,
        List<Double> dailyDistribution
) {
    /**
     * Create zero scores.
     */
    public static ComponentScoresDTO zero(PairProgrammingStatus pairProgrammingStatus) {
        return new ComponentScoresDTO(0.0, 0.0, 0.0, 0.0, null, pairProgrammingStatus, null);
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

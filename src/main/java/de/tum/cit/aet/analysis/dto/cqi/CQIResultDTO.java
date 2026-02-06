package de.tum.cit.aet.analysis.dto.cqi;

import java.util.List;

/**
 * Result of CQI calculation with component breakdown.
 *
 * @param cqi               Final CQI score (0-100)
 * @param components        Individual component scores
 * @param penalties         Applied penalties (empty - penalties are disabled)
 * @param baseScore         Score before penalties
 * @param penaltyMultiplier Combined penalty multiplier (always 1.0)
 * @param filterSummary     Summary of filtered commits
 */
public record CQIResultDTO(
        double cqi,
        ComponentScoresDTO components,
        List<CQIPenaltyDTO> penalties,
        double baseScore,
        double penaltyMultiplier,
        FilterSummaryDTO filterSummary) {
    /**
     * Create result for single contributor (no collaboration possible).
     * Returns a low but non-zero score (30) to indicate collaboration issues.
     */
    public static CQIResultDTO singleContributor() {
        return new CQIResultDTO(
                30.0, // Low but non-zero CQI
                ComponentScoresDTO.zero(),
                List.of(), // No penalties
                30.0,
                1.0,
                null);
    }

    /**
     * Create result when no productive work was found.
     */
    public static CQIResultDTO noProductiveWork(FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                0.0,
                ComponentScoresDTO.zero(),
                List.of(), // No penalties
                0.0,
                1.0,
                filterSummary);
    }

    /**
     * Create result for fallback calculation (LoC-based).
     */
    public static CQIResultDTO fallback(double locScore, FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                locScore,
                new ComponentScoresDTO(0.0, locScore, 0.0, 0.0),
                List.of(), // No penalties
                locScore,
                1.0,
                filterSummary);
    }
}

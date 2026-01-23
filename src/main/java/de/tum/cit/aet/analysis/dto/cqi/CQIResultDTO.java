package de.tum.cit.aet.analysis.dto.cqi;

import java.util.List;

/**
 * Result of CQI calculation with component breakdown.
 *
 * @param cqi               Final CQI score (0-100)
 * @param components        Individual component scores
 * @param penalties         Applied penalties (if any)
 * @param baseScore         Score before penalties
 * @param penaltyMultiplier Combined penalty multiplier
 * @param filterSummary     Summary of filtered commits
 */
public record CQIResultDTO(
        double cqi,
        ComponentScoresDTO components,
        List<CQIPenaltyDTO> penalties,
        double baseScore,
        double penaltyMultiplier,
        FilterSummaryDTO filterSummary
) {
    /**
     * Create result for single contributor (no collaboration possible).
     */
    public static CQIResultDTO singleContributor() {
        return new CQIResultDTO(
                0.0,
                ComponentScoresDTO.zero(),
                List.of(new CQIPenaltyDTO("SINGLE_CONTRIBUTOR", 0.0, "Only one contributor - no collaboration possible")),
                0.0,
                0.0,
                null
        );
    }

    /**
     * Create result when no productive work was found.
     */
    public static CQIResultDTO noProductiveWork(FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                0.0,
                ComponentScoresDTO.zero(),
                List.of(new CQIPenaltyDTO("NO_PRODUCTIVE_WORK", 0.0, "All commits were filtered as non-productive")),
                0.0,
                0.0,
                filterSummary
        );
    }

    /**
     * Create result for fallback calculation (LLM failed).
     */
    public static CQIResultDTO fallback(double locScore, FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                locScore,
                new ComponentScoresDTO(0.0, locScore, 0.0, 0.0),
                List.of(new CQIPenaltyDTO("LLM_FALLBACK", 1.0, "LLM analysis failed - using LoC-only calculation")),
                locScore,
                1.0,
                filterSummary
        );
    }
}

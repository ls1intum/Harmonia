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
     * Returns 0 since no collaboration is possible.
     */
    public static CQIResultDTO singleContributor() {
        return new CQIResultDTO(
                0.0, // No collaboration possible = 0
                ComponentScoresDTO.zero(),
                List.of(), // No penalties
                0.0,
                1.0,
                null);
    }

    /**
     * Create result when < 2/3 pair programming sessions were attended
     */
    public static CQIResultDTO noPairProgramming() {
        return new CQIResultDTO(
                0.0, // No collaboration
                ComponentScoresDTO.zero(),
                List.of(), // No penalties
                0.0,
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
                new ComponentScoresDTO(0.0, locScore, 0.0, 0.0, null),
                List.of(), // No penalties
                locScore,
                1.0,
                filterSummary);
    }

    /**
     * Create result for git-only analysis (before AI analysis).
     * Contains only git-based component scores (locBalance, temporalSpread, ownershipSpread).
     * effortBalance is 0 because it requires AI.
     * CQI is set to -1 to indicate it's not calculated yet.
     */
    public static CQIResultDTO gitOnly(ComponentScoresDTO gitComponents, FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                -1.0,  // CQI not calculated yet - client can check for this
                gitComponents,
                List.of(),
                0.0,
                1.0,
                filterSummary);
    }
}

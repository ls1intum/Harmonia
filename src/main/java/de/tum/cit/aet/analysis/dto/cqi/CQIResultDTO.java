package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.pairProgramming.enums.PairProgrammingStatus;


/**
 * Result of CQI calculation with component breakdown.
 *
 * @param cqi           final CQI score (0-100)
 * @param components    individual component scores
 * @param weights       component weights used in calculation
 * @param baseScore     score before any adjustments
 * @param filterSummary summary of filtered commits
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CQIResultDTO(
        double cqi,
        ComponentScoresDTO components,
        ComponentWeightsDTO weights,
        double baseScore,
        FilterSummaryDTO filterSummary) {
    /**
     * Create result for single contributor (no collaboration possible).
     * Returns 0 since no collaboration is possible.
     */
    public static CQIResultDTO singleContributor(ComponentWeightsDTO weights, PairProgrammingStatus pairProgrammingStatus) {
        return new CQIResultDTO(
                0.0, // No collaboration possible = 0
                ComponentScoresDTO.zero(pairProgrammingStatus),
                weights,
                0.0,
                null);
    }

    /**
     * Create result when mandatory pair-programming attendance was not met.
     */
    public static CQIResultDTO noPairProgramming(ComponentWeightsDTO weights) {
        return new CQIResultDTO(
                0.0, // No collaboration
                ComponentScoresDTO.zero(PairProgrammingStatus.FAIL),
                weights,
                0.0,
                null);
    }


    /**
     * Create result when no productive work was found.
     */
    public static CQIResultDTO noProductiveWork(ComponentWeightsDTO weights, FilterSummaryDTO filterSummary, PairProgrammingStatus pairProgrammingStatus) {
        return new CQIResultDTO(
                0.0,
                ComponentScoresDTO.zero(pairProgrammingStatus),
                weights,
                0.0,
                filterSummary);
    }

    /**
     * Create result for fallback calculation (LoC-based).
     */
    public static CQIResultDTO fallback(ComponentWeightsDTO weights, double locScore, FilterSummaryDTO filterSummary, PairProgrammingStatus pairProgrammingStatus) {
        return new CQIResultDTO(
                locScore,
                new ComponentScoresDTO(0.0, locScore, 0.0, 0.0, null, pairProgrammingStatus, null),
                weights,
                locScore,
                filterSummary);
    }

    /**
     * Create result for git-only analysis (before AI analysis).
     * Contains only git-based component scores (locBalance, temporalSpread, ownershipSpread).
     * effortBalance is 0 because it requires AI.
     * CQI is set to -1 to indicate it's not calculated yet.
     */
    public static CQIResultDTO gitOnly(ComponentWeightsDTO weights, ComponentScoresDTO gitComponents, FilterSummaryDTO filterSummary) {
        return new CQIResultDTO(
                -1.0,  // CQI not calculated yet - client can check for this
                gitComponents,
                weights,
                0.0,
                filterSummary);
    }
}

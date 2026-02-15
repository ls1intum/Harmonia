package de.tum.cit.aet.analysis.dto.cqi;

/**
 * Component weights used in CQI calculation.
 *
 * @param effortBalance   Weight for effort distribution balance (0.0-1.0)
 * @param locBalance      Weight for lines of code distribution balance (0.0-1.0)
 * @param temporalSpread  Weight for temporal distribution of work (0.0-1.0)
 * @param ownershipSpread Weight for file ownership distribution (0.0-1.0)
 */
public record ComponentWeightsDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread
) {
}

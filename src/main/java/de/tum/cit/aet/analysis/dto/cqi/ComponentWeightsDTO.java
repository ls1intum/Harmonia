package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Component weights used in CQI calculation.
 *
 * @param effortBalance   Weight for effort distribution balance (0.0-1.0)
 * @param locBalance      Weight for lines of code distribution balance (0.0-1.0)
 * @param temporalSpread  Weight for temporal distribution of work (0.0-1.0)
 * @param ownershipSpread Weight for file ownership distribution (0.0-1.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComponentWeightsDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread
) {
}

package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response DTO exposing CQI weight configuration for a specific exercise.
 *
 * @param effortBalance   weight for effort balance (0-1)
 * @param locBalance      weight for lines-of-code balance (0-1)
 * @param temporalSpread  weight for temporal spread (0-1)
 * @param ownershipSpread weight for ownership spread (0-1)
 * @param isDefault       {@code true} when the weights are application defaults
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CqiWeightsDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread,
        boolean isDefault
) {}

package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for CQI weight configuration. Used for both request (save) and response (get/reset).
 * When used as a request body, {@code isDefault} can be omitted (defaults to {@code false}).
 *
 * @param effortBalance   weight for effort balance (0-1)
 * @param locBalance      weight for lines-of-code balance (0-1)
 * @param temporalSpread  weight for temporal spread (0-1)
 * @param ownershipSpread weight for ownership spread (0-1)
 * @param isDefault       {@code true} when the weights are application defaults (response-only)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CqiWeightsDTO(
        double effortBalance,
        double locBalance,
        double temporalSpread,
        double ownershipSpread,
        @JsonProperty("isDefault") Boolean isDefault
) {
    /**
     * Canonical constructor that normalizes {@code null} isDefault to {@code false}.
     */
    public CqiWeightsDTO(double effortBalance, double locBalance,
                          double temporalSpread, double ownershipSpread,
                          Boolean isDefault) {
        this.effortBalance = effortBalance;
        this.locBalance = locBalance;
        this.temporalSpread = temporalSpread;
        this.ownershipSpread = ownershipSpread;
        this.isDefault = isDefault != null ? isDefault : false;
    }
}

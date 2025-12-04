package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Anomaly detection report.
 *
 * @param flags detected anomaly flags
 * @param confidence overall confidence (0.0-1.0)
 * @param reasons explanations for each detected anomaly
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnomalyReportDTO(
    List<AnomalyFlag> flags,
    double confidence,
    List<String> reasons
) {
    public enum AnomalyFlag {
        LATE_DUMP,
        SOLO_DEVELOPMENT,
        INACTIVE_PERIOD,
        UNEVEN_DISTRIBUTION
    }
}

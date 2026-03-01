package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Fairness report bundled with aggregated token totals for one team.
 *
 * @param report      the fairness report
 * @param tokenTotals aggregated LLM token usage across all calls for this team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FairnessReportWithUsageDTO(FairnessReportDTO report, LlmTokenTotalsDTO tokenTotals) {
}

package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * DTO for exposing analysis status to the client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisStatusDTO(
        Long exerciseId,
        String state,
        int totalTeams,
        int processedTeams,
        String currentTeamName,
        String currentStage,
        Instant startedAt,
        Instant lastUpdatedAt,
        String errorMessage,
        String analysisMode) {
}

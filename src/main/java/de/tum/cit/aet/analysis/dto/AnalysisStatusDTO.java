package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * DTO for exposing analysis status to the client.
 *
 * @param exerciseId      the exercise ID
 * @param state           current analysis state
 * @param totalTeams      total number of teams to process
 * @param processedTeams  number of teams processed so far
 * @param currentTeamName name of the team currently being processed
 * @param currentStage    current processing stage description
 * @param startedAt       when the analysis started
 * @param lastUpdatedAt   when the status was last updated
 * @param errorMessage    error message if analysis failed
 * @param analysisMode    the analysis mode (SIMPLE or FULL)
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

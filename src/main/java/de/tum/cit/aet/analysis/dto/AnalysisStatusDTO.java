package de.tum.cit.aet.analysis.dto;

import java.time.Instant;

/**
 * DTO for exposing analysis status to the frontend.
 */
public record AnalysisStatusDTO(
        Long exerciseId,
        String state,
        int totalTeams,
        int processedTeams,
        String currentTeamName,
        String currentStage,
        Instant startedAt,
        Instant lastUpdatedAt,
        String errorMessage) {
}

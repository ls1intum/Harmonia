package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO for Artemis API participation response.
 * Represents a team's participation in an exercise.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationDTO(
        Long id,
        String repositoryUri,
        TeamDTO team
) {
    /**
     * Nested DTO for team information.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDTO(
            Long id,
            String name,
            String shortName,
            List<ParticipantDTO> students,
            ParticipantDTO owner
    ) {
    }

    /**
     * Nested DTO for user information (student/owner).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantDTO(
            Long id,
            String login,
            String name
    ) {
    }
}
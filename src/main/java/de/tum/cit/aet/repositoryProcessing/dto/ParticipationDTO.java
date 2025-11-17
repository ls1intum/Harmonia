package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
}

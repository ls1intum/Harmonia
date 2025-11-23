package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Artemis API participation response.
 * Represents a team's participation in an exercise, linking the team to its repository.
 *
 * @param id              The unique identifier of the participation record.
 * @param repositoryUri   The URI or URL of the code repository associated with this participation.
 * @param submissionCount The number of submissions made by the team for this participation.
 * @param team            The team associated with this participation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationDTO(
        TeamDTO team,
        Long id,
        String repositoryUri,
        Integer submissionCount
) {
}

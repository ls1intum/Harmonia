package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Artemis API participation response.
 * Represents a team's participation in an exercise, linking the team to its repository.
 *
 * @param team            the team associated with this participation
 * @param id              the unique identifier of the participation
 * @param repositoryUri   the repository URL associated with this participation
 * @param submissionCount the number of submissions made by the team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationDTO(
        TeamDTO team,
        Long id,
        String repositoryUri,
        Integer submissionCount
) {
}

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a team's repository information and analysis results.
 *
 * @param participation The participation details, including the team and repository URI.
 * @param localPath     The path to the local copy of the repository, if successfully cloned.
 * @param isCloned      A flag indicating whether the repository was successfully cloned locally.
 * @param error         Any error message encountered during processing (e.g., cloning failure).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamRepositoryDTO(
        ParticipationDTO participation,
        String localPath,
        Boolean isCloned,
        String error
) {
    /**
     * Creates a builder for TeamRepositoryDTO.
     */
    public static TeamRepositoryDTOBuilder builder() {
        return new TeamRepositoryDTOBuilder();
    }

    /**
     * Creates a copy with updated error.
     */
    public TeamRepositoryDTO withError(String error) {
        return new TeamRepositoryDTO(participation, localPath, isCloned, error);
    }
}

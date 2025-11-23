package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a team's repository information and analysis results.
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

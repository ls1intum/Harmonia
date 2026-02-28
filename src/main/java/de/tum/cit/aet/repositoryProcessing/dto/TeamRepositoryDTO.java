package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DTO representing a team's repository information and analysis results.
 *
 * @param participation the participation details including team and repository URI
 * @param vcsLogs       list of VCS log entries associated with the repository
 * @param localPath     local filesystem path of the cloned repository
 * @param isCloned      whether the repository was successfully cloned
 * @param error         error message if processing failed (e.g. cloning failure)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamRepositoryDTO(
        ParticipationDTO participation,
        List<VCSLogDTO> vcsLogs,
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
        return new TeamRepositoryDTO(participation, vcsLogs, localPath, isCloned, error);
    }
}

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a VCS log entry.
 *
 * @param email                the committer's email
 * @param repositoryActionType the type of repository action performed
 * @param commitHash           the commit SHA hash
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VCSLogDTO(
        String email,
        String repositoryActionType,
        String commitHash
) {
}

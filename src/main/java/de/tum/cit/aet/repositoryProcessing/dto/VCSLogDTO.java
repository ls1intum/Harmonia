package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a VCS Log entry.
 *
 * @param email                The email of the committer.
 * @param repositoryActionType The type of action performed in the repository.
 * @param commitHash           The hash of the commit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VCSLogDTO(
        String email,
        String repositoryActionType,
        String commitHash
) {
}

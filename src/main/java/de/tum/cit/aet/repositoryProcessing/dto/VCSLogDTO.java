package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VCSLogDTO(
        String email,
        String repositoryActionType,
        String commitHash
) {
}
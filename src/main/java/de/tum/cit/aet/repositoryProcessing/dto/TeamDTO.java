package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDTO(
        Long id,
        String name,
        String shortName,
        List<ParticipantDTO> students,
        ParticipantDTO owner
) {
}
package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponseDTO(
        String tutor,
        Long teamId,
        String teamName,
        String shortName,
        Integer submissionCount,
        List<StudentAnalysisDTO> students
) {
}

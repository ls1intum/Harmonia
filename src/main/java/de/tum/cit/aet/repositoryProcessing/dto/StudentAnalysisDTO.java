package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentAnalysisDTO(
        String name,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged
){
}

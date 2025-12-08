package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing the analysis of a student's contributions which will be sent to the client
 *
 * @param name         The name of the student.
 * @param commitCount  The number of commits made by the student.
 * @param linesAdded   The number of lines added by the student.
 * @param linesDeleted The number of lines deleted by the student.
 * @param linesChanged The total number of lines changed by the student.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentAnalysisDTO(
        String name,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged
) {
}

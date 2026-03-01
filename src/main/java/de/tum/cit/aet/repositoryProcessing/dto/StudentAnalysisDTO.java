package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing a student's contribution statistics sent to the client.
 *
 * @param name         the student's display name
 * @param commitCount  number of commits made by the student
 * @param linesAdded   lines added by the student
 * @param linesDeleted lines deleted by the student
 * @param linesChanged total lines changed by the student
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

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lean student DTO for the teams summary endpoint.
 *
 * @param name         the student's display name
 * @param commitCount  number of commits
 * @param linesAdded   lines added
 * @param linesDeleted lines deleted
 * @param linesChanged total lines changed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentSummaryDTO(
        String name,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged
) {
    /**
     * Creates a summary DTO from a full student analysis DTO.
     *
     * @param student the full student analysis DTO
     * @return a lean summary containing only the fields needed for the Teams list
     */
    public static StudentSummaryDTO from(StudentAnalysisDTO student) {
        return new StudentSummaryDTO(
                student.name(),
                student.commitCount(),
                student.linesAdded(),
                student.linesDeleted(),
                student.linesChanged());
    }
}

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lean student DTO for the teams summary endpoint.
 * Contains only the fields needed for the Teams list page.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentSummaryDTO(
        String name,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged
) {
    public static StudentSummaryDTO from(StudentAnalysisDTO student) {
        return new StudentSummaryDTO(
                student.name(),
                student.commitCount(),
                student.linesAdded(),
                student.linesDeleted(),
                student.linesChanged());
    }
}

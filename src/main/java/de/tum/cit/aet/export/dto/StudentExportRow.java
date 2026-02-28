package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export row for student-level data.
 *
 * @param teamName     team the student belongs to
 * @param studentName  student display name
 * @param login        student login
 * @param email        student email
 * @param commitCount  number of commits
 * @param linesAdded   lines added
 * @param linesDeleted lines deleted
 * @param linesChanged total lines changed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentExportRow(
        String teamName,
        String studentName,
        String login,
        String email,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged) {
}

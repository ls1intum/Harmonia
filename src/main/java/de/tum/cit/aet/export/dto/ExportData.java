package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Container for all data scopes included in an export.
 *
 * @param teams    team-level rows (null if scope not requested)
 * @param students student-level rows (null if scope not requested)
 * @param chunks   analyzed chunk rows (null if scope not requested)
 * @param commits  commit-level rows (null if scope not requested)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportData(
        List<TeamExportRow> teams,
        List<StudentExportRow> students,
        List<ChunkExportRow> chunks,
        List<CommitExportRow> commits) {
}

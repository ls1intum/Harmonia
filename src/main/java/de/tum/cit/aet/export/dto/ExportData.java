package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportData(
        List<TeamExportRow> teams,
        List<StudentExportRow> students,
        List<ChunkExportRow> chunks,
        List<CommitExportRow> commits) {
}

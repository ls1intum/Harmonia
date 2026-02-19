package de.tum.cit.aet.export.dto;

import java.util.List;

public record ExportData(
        List<TeamExportRow> teams,
        List<StudentExportRow> students,
        List<ChunkExportRow> chunks,
        List<CommitExportRow> commits) {
}

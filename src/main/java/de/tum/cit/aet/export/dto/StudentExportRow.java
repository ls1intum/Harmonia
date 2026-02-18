package de.tum.cit.aet.export.dto;

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

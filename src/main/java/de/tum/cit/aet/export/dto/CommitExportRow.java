package de.tum.cit.aet.export.dto;

public record CommitExportRow(
        String teamName,
        String commitHash,
        String authorEmail) {
}

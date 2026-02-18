package de.tum.cit.aet.export.dto;

import java.time.LocalDateTime;

public record ChunkExportRow(
        String teamName,
        String authorName,
        String authorEmail,
        String classification,
        Double effortScore,
        Double complexity,
        Double novelty,
        Double confidence,
        String reasoning,
        String commitShas,
        LocalDateTime timestamp,
        Integer linesChanged) {
}

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
        String commitMessages,
        LocalDateTime timestamp,
        Integer linesChanged,
        Boolean isBundled,
        Integer chunkIndex,
        Integer totalChunks,
        Boolean isError,
        String errorMessage,
        Boolean isExternalContributor,
        String llmModel,
        Long llmPromptTokens,
        Long llmCompletionTokens,
        Long llmTotalTokens) {
}

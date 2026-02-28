package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Export row for analyzed chunk data.
 *
 * @param teamName           team the chunk belongs to
 * @param authorName         chunk author display name
 * @param authorEmail        chunk author email
 * @param classification     AI classification label
 * @param effortScore        effort score (1-10)
 * @param complexity         complexity score (1-10)
 * @param novelty            novelty score (1-10)
 * @param confidence         AI confidence (0.0-1.0)
 * @param reasoning          AI reasoning explanation
 * @param commitShas         comma-separated commit SHAs
 * @param commitMessages     commit messages
 * @param timestamp          chunk timestamp
 * @param linesChanged       total lines changed
 * @param isBundled          whether multiple commits were bundled
 * @param chunkIndex         index of this chunk within a split commit
 * @param totalChunks        total chunks for the parent commit
 * @param isError            whether AI analysis failed for this chunk
 * @param errorMessage       error message if analysis failed
 * @param llmModel           LLM model used for analysis
 * @param llmPromptTokens    input tokens used
 * @param llmCompletionTokens output tokens used
 * @param llmTotalTokens     total tokens used
 * @param llmUsageAvailable  whether token usage metadata was available
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        String llmModel,
        Long llmPromptTokens,
        Long llmCompletionTokens,
        Long llmTotalTokens,
        Boolean llmUsageAvailable) {
}

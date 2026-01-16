package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a chunk of a commit for LLM-based effort analysis.
 * Large commits are split into multiple chunks (max 500 LoC each).
 * Small commits from the same author within 60 minutes are bundled.
 *
 * @param commitSha      The original commit SHA (or first SHA if bundled)
 * @param authorId       The author's student ID
 * @param authorEmail    The author's email
 * @param commitMessage  The commit message (concatenated if bundled)
 * @param timestamp      The commit timestamp (earliest if bundled)
 * @param files          List of file paths modified in this chunk
 * @param diffContent    The actual diff content for this chunk
 * @param linesAdded     Number of lines added in this chunk
 * @param linesDeleted   Number of lines deleted in this chunk
 * @param chunkIndex     Index of this chunk (0 if not chunked)
 * @param totalChunks    Total number of chunks for this commit (1 if not
 *                       chunked)
 * @param isBundled      True if this represents multiple small commits bundled
 *                       together
 * @param bundledCommits List of commit SHAs if bundled, empty otherwise
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitChunkDTO(
        String commitSha,
        Long authorId,
        String authorEmail,
        String commitMessage,
        LocalDateTime timestamp,
        List<String> files,
        String diffContent,
        int linesAdded,
        int linesDeleted,
        int chunkIndex,
        int totalChunks,
        boolean isBundled,
        List<String> bundledCommits) {
    /**
     * Creates a simple chunk from a single commit (not bundled, not split).
     */
    public static CommitChunkDTO single(
            String commitSha,
            Long authorId,
            String authorEmail,
            String commitMessage,
            LocalDateTime timestamp,
            List<String> files,
            String diffContent,
            int linesAdded,
            int linesDeleted) {
        return new CommitChunkDTO(
                commitSha, authorId, authorEmail, commitMessage, timestamp,
                files, diffContent, linesAdded, linesDeleted,
                0, 1, false, List.of());
    }

    /**
     * Total lines changed (added + deleted).
     */
    public int totalLinesChanged() {
        return linesAdded + linesDeleted;
    }
}

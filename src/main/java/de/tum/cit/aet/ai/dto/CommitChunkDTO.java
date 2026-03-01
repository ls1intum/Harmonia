package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a chunk of a commit for LLM-based effort analysis.
 * Large commits are split into multiple chunks (max 500 LoC each).
 * Small commits from the same author within 60 minutes are bundled.
 *
 * @param commitSha        the original commit SHA (or first SHA if bundled)
 * @param authorId         the author's student ID
 * @param authorEmail      the author's email
 * @param commitMessage    the commit message (concatenated if bundled)
 * @param timestamp        the commit timestamp (earliest if bundled)
 * @param files            list of file paths modified in this chunk
 * @param diffContent      the actual diff content for this chunk
 * @param linesAdded       lines added in this chunk
 * @param linesDeleted     lines deleted in this chunk
 * @param chunkIndex       index of this chunk (0 if not chunked)
 * @param totalChunks      total number of chunks for this commit (1 if not chunked)
 * @param isBundled        whether this represents multiple small commits bundled together
 * @param bundledCommits   list of commit SHAs if bundled, empty otherwise
 * @param renameDetected   whether git detected file renames (-M/-C flag)
 * @param formatOnly       whether only whitespace/formatting changes were detected
 * @param massReformatFlag whether detected as mass reformat (many files, uniform patterns)
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
        List<String> bundledCommits,
        Boolean renameDetected,
        Boolean formatOnly,
        Boolean massReformatFlag) {

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
                0, 1, false, List.of(), null, null, null);
    }

    /**
     * Creates a chunk with filter detection flags.
     */
    public static CommitChunkDTO withFlags(
            String commitSha,
            Long authorId,
            String authorEmail,
            String commitMessage,
            LocalDateTime timestamp,
            List<String> files,
            String diffContent,
            int linesAdded,
            int linesDeleted,
            Boolean renameDetected,
            Boolean formatOnly,
            Boolean massReformatFlag) {
        return new CommitChunkDTO(
                commitSha, authorId, authorEmail, commitMessage, timestamp,
                files, diffContent, linesAdded, linesDeleted,
                0, 1, false, List.of(), renameDetected, formatOnly, massReformatFlag);
    }

    /**
     * Total lines changed (added + deleted).
     */
    public int totalLinesChanged() {
        return linesAdded + linesDeleted;
    }
}

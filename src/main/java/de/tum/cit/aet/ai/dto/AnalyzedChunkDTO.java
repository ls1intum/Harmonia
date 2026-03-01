package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a single analyzed chunk sent to the client.
 * Contains the AI's classification, reasoning, and the commits that were
 * analyzed together.
 *
 * @param id                    unique chunk identifier
 * @param authorEmail           the chunk author's email
 * @param authorName            the chunk author's display name
 * @param classification        AI classification label
 * @param effortScore           effort score (1-10)
 * @param complexity            complexity score (1-10)
 * @param novelty               novelty score (1-10)
 * @param confidence            AI confidence (0.0-1.0)
 * @param reasoning             AI reasoning explanation
 * @param commitShas            list of commit SHAs in this chunk
 * @param commitMessages        list of commit messages in this chunk
 * @param timestamp             chunk timestamp (earliest if bundled)
 * @param linesChanged          total lines changed
 * @param isBundled             whether multiple commits were bundled together
 * @param chunkIndex            index of this chunk within a split commit
 * @param totalChunks           total chunks for the parent commit
 * @param isError               whether AI analysis failed for this chunk
 * @param errorMessage          error message if analysis failed
 * @param isExternalContributor whether the author is an external contributor
 * @param llmTokenUsage         token usage metadata for this chunk's analysis
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnalyzedChunkDTO(
                String id,
                String authorEmail,
                String authorName,
                String classification,
                double effortScore,
                double complexity,
                double novelty,
                double confidence,
                String reasoning,
                List<String> commitShas,
                List<String> commitMessages,
                LocalDateTime timestamp,
                int linesChanged,
                boolean isBundled,
                int chunkIndex,
                int totalChunks,
                boolean isError,
                String errorMessage,
                boolean isExternalContributor,
                LlmTokenUsageDTO llmTokenUsage) {

        /**
         * Creates a simple chunk for a single commit analysis.
         */
        public static AnalyzedChunkDTO single(
                        String sha, String authorEmail, String authorName,
                        String classification, double effort, double complexity, double novelty,
                        double confidence, String reasoning,
                        String message, LocalDateTime timestamp, int linesChanged) {
                return new AnalyzedChunkDTO(
                                sha, authorEmail, authorName, classification, effort, complexity, novelty, confidence, reasoning,
                                List.of(sha), List.of(message), timestamp, linesChanged,
                                false, 0, 1, false, null, false, null);
        }

        /**
         * Creates a bundled chunk for multiple small commits analyzed together.
         */
        public static AnalyzedChunkDTO bundled(
                        String id, String authorEmail, String authorName,
                        String classification, double effort, double complexity, double novelty,
                        double confidence, String reasoning,
                        List<String> shas, List<String> messages,
                        LocalDateTime timestamp, int linesChanged) {
                return new AnalyzedChunkDTO(
                                id, authorEmail, authorName, classification, effort, complexity, novelty, confidence, reasoning,
                                shas, messages, timestamp, linesChanged,
                                true, 0, 1, false, null, false, null);
        }

        /**
         * Creates an error chunk for when AI analysis fails.
         */
        public static AnalyzedChunkDTO error(
                        String sha, String authorEmail, String authorName,
                        String message, LocalDateTime timestamp, int linesChanged,
                        String errorMessage) {
                return new AnalyzedChunkDTO(
                                sha, authorEmail, authorName, "ERROR", 0.0, 0.0, 0.0, 0.0, errorMessage,
                                List.of(sha), List.of(message), timestamp, linesChanged,
                                false, 0, 1, true, errorMessage, false, null);
        }

        /**
         * Creates a copy of this chunk marked as external contributor.
         */
        public AnalyzedChunkDTO withExternalContributor(boolean isExternal) {
                return new AnalyzedChunkDTO(
                                id, authorEmail, authorName, classification, effortScore, complexity, novelty, confidence, reasoning,
                                commitShas, commitMessages, timestamp, linesChanged,
                                isBundled, chunkIndex, totalChunks, isError, errorMessage, isExternal, llmTokenUsage);
        }
}

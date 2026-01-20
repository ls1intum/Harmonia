package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a single analyzed chunk sent to the client.
 * Contains the AI's classification, reasoning, and the commits that were
 * analyzed together.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnalyzedChunkDTO(
                String id,
                String authorEmail,
                String authorName,
                String classification,
                double effortScore,
                String reasoning,
                List<String> commitShas,
                List<String> commitMessages,
                LocalDateTime timestamp,
                int linesChanged,
                boolean isBundled,
                int chunkIndex,
                int totalChunks,
                boolean isError,
                String errorMessage) {
        /**
         * Creates a simple chunk for a single commit analysis.
         */
        public static AnalyzedChunkDTO single(
                        String sha, String authorEmail, String authorName,
                        String classification, double effort, String reasoning,
                        String message, LocalDateTime timestamp, int linesChanged) {
                return new AnalyzedChunkDTO(
                                sha, authorEmail, authorName, classification, effort, reasoning,
                                List.of(sha), List.of(message), timestamp, linesChanged,
                                false, 0, 1, false, null);
        }

        /**
         * Creates a bundled chunk for multiple small commits analyzed together.
         */
        public static AnalyzedChunkDTO bundled(
                        String id, String authorEmail, String authorName,
                        String classification, double effort, String reasoning,
                        List<String> shas, List<String> messages,
                        LocalDateTime timestamp, int linesChanged) {
                return new AnalyzedChunkDTO(
                                id, authorEmail, authorName, classification, effort, reasoning,
                                shas, messages, timestamp, linesChanged,
                                true, 0, 1, false, null);
        }

        /**
         * Creates an error chunk for when AI analysis fails.
         */
        public static AnalyzedChunkDTO error(
                        String sha, String authorEmail, String authorName,
                        String message, LocalDateTime timestamp, int linesChanged,
                        String errorMessage) {
                return new AnalyzedChunkDTO(
                                sha, authorEmail, authorName, "ERROR", 0.0, errorMessage,
                                List.of(sha), List.of(message), timestamp, linesChanged,
                                false, 0, 1, true, errorMessage);
        }
}

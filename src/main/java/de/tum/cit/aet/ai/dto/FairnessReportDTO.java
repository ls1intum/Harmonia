package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import java.util.List;
import java.util.Map;

/**
 * Final fairness analysis report for a team repository.
 *
 * @param teamId              the team identifier
 * @param balanceScore        overall balance score (0-100, 100 = perfectly balanced)
 * @param effortByAuthor      map of author ID to their total weighted effort points
 * @param effortShareByAuthor map of author ID to their percentage share (0.0-1.0)
 * @param error               true if the analysis failed with an error
 * @param authorDetails       detailed contribution breakdown per author
 * @param analysisMetadata    metadata about the analysis process
 * @param analyzedChunks      list of analyzed chunks for this team
 * @param cqiResult           detailed CQI calculation result with components
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FairnessReportDTO(
                String teamId,
                double balanceScore,
                Map<Long, Double> effortByAuthor,
                Map<Long, Double> effortShareByAuthor,
                boolean error,
                List<AuthorDetailDTO> authorDetails,
                AnalysisMetadataDTO analysisMetadata,
                List<AnalyzedChunkDTO> analyzedChunks,
                CQIResultDTO cqiResult) {
        /**
         * Detailed contribution breakdown for a single author.
         *
         * @param authorId             the author's student ID
         * @param authorEmail          the author's email
         * @param totalEffort          total weighted effort points
         * @param effortShare          percentage share of total effort (0.0-1.0)
         * @param commitCount          number of commits
         * @param chunkCount           number of analyzed chunks
         * @param averageEffortPerChunk average effort score per chunk
         * @param commitsByType        breakdown of commits by classification type
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AuthorDetailDTO(
                        Long authorId,
                        String authorEmail,
                        double totalEffort,
                        double effortShare,
                        int commitCount,
                        int chunkCount,
                        double averageEffortPerChunk,
                        Map<CommitLabel, Integer> commitsByType) {
        }

        /**
         * Metadata about the analysis process.
         *
         * @param totalCommits        total commits analyzed
         * @param totalChunks         total chunks created
         * @param bundledCommitGroups number of bundled commit groups
         * @param averageConfidence   average AI confidence across all ratings
         * @param lowConfidenceRatings number of ratings with low confidence
         * @param analysisTimeMs      total analysis time in milliseconds
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AnalysisMetadataDTO(
                        int totalCommits,
                        int totalChunks,
                        int bundledCommitGroups,
                        double averageConfidence,
                        int lowConfidenceRatings,
                        long analysisTimeMs) {
        }

        /**
         * Creates an error report when analysis fails.
         */
        public static FairnessReportDTO error(String teamId, String errorMessage) {
                return new FairnessReportDTO(
                                teamId,
                                0.0,
                                Map.of(),
                                Map.of(),
                                true,
                                List.of(),
                                new AnalysisMetadataDTO(0, 0, 0, 0.0, 0, 0),
                                List.of(),
                                null);
        }
}

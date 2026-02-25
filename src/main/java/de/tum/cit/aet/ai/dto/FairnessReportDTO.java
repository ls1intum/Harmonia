package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import java.util.List;
import java.util.Map;

/**
 * Final fairness analysis report for a team repository.
 *
 * @param teamId               The team identifier
 * @param balanceScore         Overall balance score (0-100). 100 = perfectly
 *                             balanced.
 * @param effortByAuthor       Map of author ID to their total weighted effort
 *                             points
 * @param effortShareByAuthor  Map of author ID to their percentage share
 *                             (0.0-1.0)
 * @param error                True if the analysis failed with an error
 * @param authorDetails        Detailed breakdown per author
 * @param analysisMetadata     Metadata about the analysis (commit count, chunk
 *                             count, etc.)
 * @param cqiResult            Detailed CQI calculation result with components
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

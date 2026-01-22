package de.tum.cit.aet.analysis.dto.cqi;

import de.tum.cit.aet.ai.dto.CommitLabel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Input data for CQI calculation.
 *
 * @param teamSize              Number of team members
 * @param effortByAuthor        Map of author ID to their total weighted effort
 * @param locByAuthor           Map of author ID to their total lines changed
 * @param commitsByType         Aggregated commit types across all authors
 * @param chunks                List of rated commit chunks (after filtering)
 * @param projectStart          Project start date (first commit or assignment start)
 * @param projectEnd            Project end date (last commit or deadline)
 * @param averageConfidence     Average LLM confidence across all ratings
 * @param lowConfidenceCount    Number of ratings with confidence < 0.6
 */
public record CQIInputDTO(
        int teamSize,
        Map<Long, Double> effortByAuthor,
        Map<Long, Integer> locByAuthor,
        Map<CommitLabel, Integer> commitsByType,
        List<FilteredChunkDTO> chunks,
        LocalDateTime projectStart,
        LocalDateTime projectEnd,
        double averageConfidence,
        int lowConfidenceCount
) {
    /**
     * Calculate effort share for each author.
     */
    public Map<Long, Double> getEffortShares() {
        double total = effortByAuthor.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) {
            return Map.of();
        }
        return effortByAuthor.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() / total
                ));
    }

    /**
     * Calculate LoC share for each author.
     */
    public Map<Long, Double> getLocShares() {
        double total = locByAuthor.values().stream().mapToDouble(Integer::doubleValue).sum();
        if (total == 0) {
            return Map.of();
        }
        return locByAuthor.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() / total
                ));
    }

    /**
     * Get the ratio of low confidence ratings.
     */
    public double getLowConfidenceRatio() {
        int totalChunks = chunks != null ? chunks.size() : 0;
        if (totalChunks == 0) {
            return 0.0;
        }
        return (double) lowConfidenceCount / totalChunks;
    }
}

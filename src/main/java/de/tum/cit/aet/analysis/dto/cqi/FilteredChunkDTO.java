package de.tum.cit.aet.analysis.dto.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A commit chunk after filtering, with applied weight adjustments.
 *
 * @param originalChunk   The original commit chunk
 * @param rating          The LLM effort rating
 * @param weightMultiplier Weight adjustment (1.0 = full, 0.1 = reduced for copy-paste)
 * @param filterReason    Reason if filtered (null if kept)
 * @param isFiltered      Whether this chunk was filtered out
 */
public record FilteredChunkDTO(
        CommitChunkDTO originalChunk,
        EffortRatingDTO rating,
        double weightMultiplier,
        String filterReason,
        boolean isFiltered
) {
    /**
     * Create a productive (kept) chunk with full weight.
     */
    public static FilteredChunkDTO productive(CommitChunkDTO chunk, EffortRatingDTO rating) {
        return new FilteredChunkDTO(chunk, rating, 1.0, null, false);
    }

    /**
     * Create a productive chunk with reduced weight (e.g., copy-paste).
     */
    public static FilteredChunkDTO reducedWeight(CommitChunkDTO chunk, EffortRatingDTO rating,
                                                  double weight, String reason) {
        return new FilteredChunkDTO(chunk, rating, weight, reason, false);
    }

    /**
     * Create a filtered (excluded) chunk.
     */
    public static FilteredChunkDTO filtered(CommitChunkDTO chunk, EffortRatingDTO rating, String reason) {
        return new FilteredChunkDTO(chunk, rating, 0.0, reason, true);
    }

    // Convenience accessors
    public Long authorId() {
        return originalChunk.authorId();
    }

    public String authorEmail() {
        return originalChunk.authorEmail();
    }

    public LocalDateTime timestamp() {
        return originalChunk.timestamp();
    }

    public List<String> files() {
        return originalChunk.files();
    }

    public int linesChanged() {
        return originalChunk.totalLinesChanged();
    }

    public CommitLabel commitType() {
        return rating != null ? rating.type() : CommitLabel.TRIVIAL;
    }

    /**
     * Get the effective (weighted) effort for this chunk.
     */
    public double effectiveEffort() {
        if (isFiltered || rating == null) {
            return 0.0;
        }
        return rating.weightedEffort() * weightMultiplier;
    }

    /**
     * Get the effective (weighted) lines changed.
     */
    public int effectiveLinesChanged() {
        if (isFiltered) {
            return 0;
        }
        return (int) (linesChanged() * weightMultiplier);
    }
}

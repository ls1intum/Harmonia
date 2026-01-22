package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilteredChunkDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for filtering non-productive commits before CQI calculation.
 * <p>
 * Filters out commits that don't represent real collaborative work:
 * - Trivial commits (typos, formatting, whitespace)
 * - Auto-generated code (build artifacts, lock files)
 * - Copy-pasted content (detected via low novelty + high LoC)
 * - Empty commits
 * - Merge commits
 */
@Service
@Slf4j
public class CommitFilterService {

    // Thresholds for detection
    private static final double LOW_NOVELTY_THRESHOLD = 3.0;
    private static final double LOW_EFFORT_THRESHOLD = 2.0;
    private static final double COPY_PASTE_NOVELTY_THRESHOLD = 2.0;
    private static final double COPY_PASTE_COMPLEXITY_THRESHOLD = 3.0;
    private static final int COPY_PASTE_LOC_THRESHOLD = 100;
    private static final int TRIVIAL_LOC_THRESHOLD = 5;
    private static final double COPY_PASTE_WEIGHT_REDUCTION = 0.1;

    // Patterns for auto-generated files
    private static final Set<String> GENERATED_FILE_PATTERNS = Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "Cargo.lock",
            "Gemfile.lock",
            "poetry.lock",
            "composer.lock"
    );

    private static final List<Pattern> GENERATED_PATH_PATTERNS = List.of(
            Pattern.compile(".*\\.min\\.(js|css)$"),
            Pattern.compile(".*\\.generated\\.[a-z]+$"),
            Pattern.compile("^node_modules/.*"),
            Pattern.compile("^build/.*"),
            Pattern.compile("^dist/.*"),
            Pattern.compile("^target/.*"),
            Pattern.compile("^out/.*"),
            Pattern.compile("^\\.gradle/.*"),
            Pattern.compile("^vendor/.*")
    );

    private static final List<Pattern> MERGE_COMMIT_PATTERNS = List.of(
            Pattern.compile("^Merge (branch|pull request|remote-tracking).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^Merge '.*' into .*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Input for filtering: commit chunk paired with its LLM rating.
     */
    public record RatedChunk(CommitChunkDTO chunk, EffortRatingDTO rating) {
    }

    /**
     * Result of filtering process.
     */
    public record FilterResult(
            List<FilteredChunkDTO> productiveChunks,
            List<FilteredChunkDTO> filteredChunks,
            FilterSummaryDTO summary
    ) {
    }

    /**
     * Filter commits to separate productive work from non-productive commits.
     *
     * @param ratedChunks List of commit chunks with their LLM ratings
     * @return FilterResult containing productive and filtered chunks
     */
    public FilterResult filterCommits(List<RatedChunk> ratedChunks) {
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            log.warn("No commits provided for filtering");
            return new FilterResult(List.of(), List.of(), FilterSummaryDTO.empty());
        }

        List<FilteredChunkDTO> productive = new ArrayList<>();
        List<FilteredChunkDTO> filtered = new ArrayList<>();

        int trivialCount = 0;
        int autoGenCount = 0;
        int copyPasteCount = 0;
        int emptyCount = 0;
        int mergeCount = 0;

        for (RatedChunk rc : ratedChunks) {
            CommitChunkDTO chunk = rc.chunk();
            EffortRatingDTO rating = rc.rating();

            // Check filters in priority order
            FilterDecision decision = evaluateChunk(chunk, rating);

            switch (decision.action()) {
                case EXCLUDE_EMPTY -> {
                    emptyCount++;
                    filtered.add(FilteredChunkDTO.filtered(chunk, rating, "Empty commit"));
                }
                case EXCLUDE_MERGE -> {
                    mergeCount++;
                    filtered.add(FilteredChunkDTO.filtered(chunk, rating, "Merge commit"));
                }
                case EXCLUDE_AUTO_GENERATED -> {
                    autoGenCount++;
                    filtered.add(FilteredChunkDTO.filtered(chunk, rating, decision.reason()));
                }
                case EXCLUDE_TRIVIAL -> {
                    trivialCount++;
                    filtered.add(FilteredChunkDTO.filtered(chunk, rating, decision.reason()));
                }
                case REDUCE_WEIGHT_COPY_PASTE -> {
                    copyPasteCount++;
                    productive.add(FilteredChunkDTO.reducedWeight(
                            chunk, rating, COPY_PASTE_WEIGHT_REDUCTION, decision.reason()));
                }
                case KEEP -> productive.add(FilteredChunkDTO.productive(chunk, rating));
            }
        }

        FilterSummaryDTO summary = new FilterSummaryDTO(
                ratedChunks.size(),
                productive.size(),
                trivialCount,
                autoGenCount,
                copyPasteCount,
                emptyCount,
                mergeCount
        );

        log.info("Commit filtering complete: {}", summary.toSummary());

        return new FilterResult(productive, filtered, summary);
    }

    /**
     * Evaluate a single chunk and determine filter action.
     */
    private FilterDecision evaluateChunk(CommitChunkDTO chunk, EffortRatingDTO rating) {
        // 1. Empty commit
        if (isEmpty(chunk)) {
            return new FilterDecision(FilterAction.EXCLUDE_EMPTY, "No code changes");
        }

        // 2. Merge commit
        if (isMergeCommit(chunk)) {
            return new FilterDecision(FilterAction.EXCLUDE_MERGE, "Merge commit");
        }

        // 3. Auto-generated files
        String autoGenReason = detectAutoGenerated(chunk, rating);
        if (autoGenReason != null) {
            return new FilterDecision(FilterAction.EXCLUDE_AUTO_GENERATED, autoGenReason);
        }

        // 4. Copy-paste detection (reduce weight, don't exclude)
        String copyPasteReason = detectCopyPaste(chunk, rating);
        if (copyPasteReason != null) {
            return new FilterDecision(FilterAction.REDUCE_WEIGHT_COPY_PASTE, copyPasteReason);
        }

        // 5. Trivial commit
        String trivialReason = detectTrivial(chunk, rating);
        if (trivialReason != null) {
            return new FilterDecision(FilterAction.EXCLUDE_TRIVIAL, trivialReason);
        }

        // Keep the commit
        return new FilterDecision(FilterAction.KEEP, null);
    }

    /**
     * Check if commit is empty.
     */
    private boolean isEmpty(CommitChunkDTO chunk) {
        return chunk.totalLinesChanged() == 0;
    }

    /**
     * Check if commit is a merge commit.
     */
    private boolean isMergeCommit(CommitChunkDTO chunk) {
        String message = chunk.commitMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        return MERGE_COMMIT_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(message).matches());
    }

    /**
     * Detect auto-generated code.
     *
     * @return Reason string if auto-generated, null otherwise
     */
    private String detectAutoGenerated(CommitChunkDTO chunk, EffortRatingDTO rating) {
        // Check for known generated files
        for (String file : chunk.files()) {
            String fileName = getFileName(file);

            // Exact match for lock files etc.
            if (GENERATED_FILE_PATTERNS.contains(fileName)) {
                return "Generated file: " + fileName;
            }

            // Pattern match for minified files, build outputs, etc.
            for (Pattern pattern : GENERATED_PATH_PATTERNS) {
                if (pattern.matcher(file).matches()) {
                    return "Generated/build output: " + file;
                }
            }
        }

        // LLM-based detection: very low novelty + low effort = likely generated
        if (rating != null && !rating.isError()) {
            if (rating.novelty() < LOW_NOVELTY_THRESHOLD && rating.effortScore() < LOW_EFFORT_THRESHOLD) {
                // Additional check: high LoC with low novelty suggests generation
                if (chunk.totalLinesChanged() > 50) {
                    return "LLM detected: low novelty + low effort with high LoC";
                }
            }
        }

        return null;
    }

    /**
     * Detect copy-pasted content.
     *
     * @return Reason string if copy-paste detected, null otherwise
     */
    private String detectCopyPaste(CommitChunkDTO chunk, EffortRatingDTO rating) {
        if (rating == null || rating.isError()) {
            return null;
        }

        // High LoC + very low novelty + low complexity = likely copy-paste
        boolean highLoc = chunk.totalLinesChanged() > COPY_PASTE_LOC_THRESHOLD;
        boolean lowNovelty = rating.novelty() < COPY_PASTE_NOVELTY_THRESHOLD;
        boolean lowComplexity = rating.complexity() < COPY_PASTE_COMPLEXITY_THRESHOLD;

        if (highLoc && lowNovelty && lowComplexity) {
            return String.format("Suspected copy-paste: %d lines, novelty=%.1f, complexity=%.1f",
                    chunk.totalLinesChanged(), rating.novelty(), rating.complexity());
        }

        return null;
    }

    /**
     * Detect trivial commits.
     *
     * @return Reason string if trivial, null otherwise
     */
    private String detectTrivial(CommitChunkDTO chunk, EffortRatingDTO rating) {
        // LLM classified as trivial
        if (rating != null && !rating.isError() && rating.type() == CommitLabel.TRIVIAL) {
            return "LLM classified as trivial: " + rating.reasoning();
        }

        // Backup: very small change with low effort
        if (chunk.totalLinesChanged() < TRIVIAL_LOC_THRESHOLD) {
            if (rating == null || rating.isError() || rating.effortScore() < LOW_EFFORT_THRESHOLD) {
                return "Very small commit: " + chunk.totalLinesChanged() + " lines";
            }
        }

        // Check commit message for trivial indicators
        String message = chunk.commitMessage();
        if (message != null && isTrivialMessage(message)) {
            if (chunk.totalLinesChanged() < 20) {
                return "Trivial commit message pattern";
            }
        }

        return null;
    }

    /**
     * Check if commit message indicates trivial change.
     */
    private boolean isTrivialMessage(String message) {
        String lower = message.toLowerCase().trim();
        return lower.matches("^(fix(ed)?|update(d)?|typo|wip|tmp|temp|test|testing)\\s*$") ||
                lower.contains("whitespace") ||
                lower.contains("formatting") ||
                lower.contains("lint") ||
                lower.matches("^(\\.|\\.\\.\\.|oops|hmm|idk|stuff)$");
    }

    /**
     * Extract filename from path.
     */
    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Filter action types.
     */
    private enum FilterAction {
        KEEP,
        EXCLUDE_EMPTY,
        EXCLUDE_MERGE,
        EXCLUDE_AUTO_GENERATED,
        EXCLUDE_TRIVIAL,
        REDUCE_WEIGHT_COPY_PASTE
    }

    /**
     * Internal decision record.
     */
    private record FilterDecision(FilterAction action, String reason) {
    }
}

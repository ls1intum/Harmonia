package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pre-filter service for commits BEFORE LLM analysis.
 * <p>
 * Filters out commits that don't represent real collaborative work
 * to save LLM API calls and improve CQI accuracy.
 * <p>
 * Filtered commits are NOT sent to the LLM and NOT counted in CQI.
 */
@Service
@Slf4j
public class CommitPreFilterService {

    // ==================== File Patterns ====================

    /**
     * Lock files and dependency manifests that are auto-generated.
     */
    private static final Set<String> GENERATED_FILES = Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "Cargo.lock",
            "Gemfile.lock",
            "poetry.lock",
            "composer.lock",
            "go.sum",
            "Pipfile.lock",
            ".gradle",
            "gradle-wrapper.jar"
    );

    /**
     * Directories containing auto-generated or third-party code.
     */
    private static final List<Pattern> GENERATED_PATH_PATTERNS = List.of(
            Pattern.compile("^node_modules/.*"),
            Pattern.compile("^build/.*"),
            Pattern.compile("^dist/.*"),
            Pattern.compile("^target/.*"),
            Pattern.compile("^out/.*"),
            Pattern.compile("^\\.gradle/.*"),
            Pattern.compile("^vendor/.*"),
            Pattern.compile("^__pycache__/.*"),
            Pattern.compile(".*\\.min\\.(js|css)$"),
            Pattern.compile(".*\\.generated\\.[a-z]+$"),
            Pattern.compile(".*\\.g\\.dart$"),  // Flutter generated
            Pattern.compile(".*\\.freezed\\.dart$")  // Dart freezed
    );

    // ==================== Commit Message Patterns ====================

    /**
     * Merge commit patterns.
     */
    private static final List<Pattern> MERGE_PATTERNS = List.of(
            Pattern.compile("^Merge (branch|pull request|remote-tracking).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^Merge '.*' into .*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^Merged .*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Trivial/housekeeping commit message patterns.
     * These commits typically don't represent real collaborative effort.
     */
    private static final List<Pattern> TRIVIAL_MESSAGE_PATTERNS = List.of(
            // Linting & Formatting
            Pattern.compile("^(fix|run|apply|format).*lint(ing)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^lint(ing)?\\s*(fix(es)?)?\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(apply|run)\\s+(prettier|eslint|checkstyle|spotless).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^format(ting)?\\s*(code|files)?\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^code\\s*format(ting)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(fix|apply)\\s+format(ting)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^prettier.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^eslint.*fix.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^checkstyle.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^spotless.*", Pattern.CASE_INSENSITIVE),

            // Whitespace & Style
            Pattern.compile("^(fix|remove)\\s*(trailing)?\\s*whitespace.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^whitespace.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(fix|update)\\s+indentation.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^style:\\s*.*", Pattern.CASE_INSENSITIVE),

            // Typos & Minor Fixes
            Pattern.compile("^(fix|correct)\\s*(a)?\\s*typo(s)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^typo(s)?\\s*(fix)?\\s*$", Pattern.CASE_INSENSITIVE),

            // WIP & Temp
            Pattern.compile("^wip\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^wip:?\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(temp|tmp|test|testing)\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\.\\.?\\.?\\s*$"),  // ".", "..", "..."
            Pattern.compile("^(oops|hmm|idk|stuff|changes|update|fix)\\s*$", Pattern.CASE_INSENSITIVE),

            // Auto-generated commits
            Pattern.compile("^auto(-)?format.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(update|bump)\\s+dependencies.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\[bot\\].*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^chore\\(deps\\).*", Pattern.CASE_INSENSITIVE),

            // Initial/empty commits
            Pattern.compile("^initial commit\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^first commit\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^init\\s*$", Pattern.CASE_INSENSITIVE)
    );

    // ==================== Thresholds ====================

    /**
     * Commits with fewer lines are suspicious if they have trivial messages.
     */
    private static final int SMALL_COMMIT_THRESHOLD = 5;

    /**
     * Result of the pre-filtering process.
     */
    public record PreFilterResult(
            List<CommitChunkDTO> chunksToAnalyze,
            List<PreFilteredCommit> filteredChunks,
            FilterSummaryDTO summary
    ) {
    }

    /**
     * A commit that was filtered out with the reason.
     */
    public record PreFilteredCommit(
            CommitChunkDTO chunk,
            FilterReason reason,
            String details
    ) {
    }

    /**
     * Reasons why a commit was filtered.
     */
    public enum FilterReason {
        EMPTY,
        MERGE_COMMIT,
        GENERATED_FILES_ONLY,
        TRIVIAL_MESSAGE,
        SMALL_TRIVIAL_COMMIT
    }

    /**
     * Filter commits BEFORE sending them to LLM analysis.
     *
     * @param chunks Raw commit chunks from Git analysis
     * @return PreFilterResult with chunks to analyze and filtered commits
     */
    public PreFilterResult preFilter(List<CommitChunkDTO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No commits provided for pre-filtering");
            return new PreFilterResult(List.of(), List.of(), FilterSummaryDTO.empty());
        }

        List<CommitChunkDTO> toAnalyze = new ArrayList<>();
        List<PreFilteredCommit> filtered = new ArrayList<>();

        int emptyCount = 0;
        int mergeCount = 0;
        int generatedCount = 0;
        int trivialCount = 0;

        for (CommitChunkDTO chunk : chunks) {
            FilterDecision decision = evaluateChunk(chunk);

            if (decision.shouldFilter()) {
                filtered.add(new PreFilteredCommit(chunk, decision.reason(), decision.details()));

                switch (decision.reason()) {
                    case EMPTY -> emptyCount++;
                    case MERGE_COMMIT -> mergeCount++;
                    case GENERATED_FILES_ONLY -> generatedCount++;
                    case TRIVIAL_MESSAGE, SMALL_TRIVIAL_COMMIT -> trivialCount++;
                }
            } else {
                toAnalyze.add(chunk);
            }
        }

        FilterSummaryDTO summary = new FilterSummaryDTO(
                chunks.size(),
                toAnalyze.size(),
                trivialCount,
                generatedCount,
                0,  // copyPasteCount - detected by LLM, not pre-filter
                emptyCount,
                mergeCount
        );

        log.info("Pre-filter complete: {} of {} commits will be analyzed. Filtered: {} empty, {} merge, {} generated, {} trivial",
                toAnalyze.size(), chunks.size(), emptyCount, mergeCount, generatedCount, trivialCount);

        return new PreFilterResult(toAnalyze, filtered, summary);
    }

    /**
     * Evaluate a single chunk and decide if it should be filtered.
     */
    private FilterDecision evaluateChunk(CommitChunkDTO chunk) {
        // 1. Empty commit
        if (chunk.totalLinesChanged() == 0) {
            return FilterDecision.filter(FilterReason.EMPTY, "No code changes");
        }

        // 2. Merge commit
        if (isMergeCommit(chunk.commitMessage())) {
            return FilterDecision.filter(FilterReason.MERGE_COMMIT, "Merge commit");
        }

        // 3. Only generated/lock files modified
        if (hasOnlyGeneratedFiles(chunk.files())) {
            return FilterDecision.filter(FilterReason.GENERATED_FILES_ONLY,
                    "Only generated files: " + String.join(", ", chunk.files()));
        }

        // 4. Trivial commit message pattern
        String trivialMatch = matchesTrivialPattern(chunk.commitMessage());
        if (trivialMatch != null) {
            // For small commits with trivial messages, always filter
            if (chunk.totalLinesChanged() <= SMALL_COMMIT_THRESHOLD) {
                return FilterDecision.filter(FilterReason.SMALL_TRIVIAL_COMMIT,
                        "Small commit (" + chunk.totalLinesChanged() + " lines) with trivial message: " + trivialMatch);
            }
            // For larger commits with trivial messages, still filter but log
            return FilterDecision.filter(FilterReason.TRIVIAL_MESSAGE,
                    "Trivial message pattern: " + trivialMatch);
        }

        // Keep the commit for LLM analysis
        return FilterDecision.keep();
    }

    /**
     * Check if commit message matches merge patterns.
     */
    private boolean isMergeCommit(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return MERGE_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(message.trim()).matches());
    }

    /**
     * Check if all files in the commit are generated/lock files.
     */
    private boolean hasOnlyGeneratedFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }

        return files.stream().allMatch(this::isGeneratedFile);
    }

    /**
     * Check if a single file is a generated file.
     */
    private boolean isGeneratedFile(String filePath) {
        String fileName = getFileName(filePath);

        // Exact match for known generated files
        if (GENERATED_FILES.contains(fileName)) {
            return true;
        }

        // Pattern match for generated paths/extensions
        return GENERATED_PATH_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(filePath).matches());
    }

    /**
     * Check if commit message matches any trivial pattern.
     *
     * @return The matched pattern description, or null if no match
     */
    private String matchesTrivialPattern(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String trimmed = message.trim();

        for (Pattern pattern : TRIVIAL_MESSAGE_PATTERNS) {
            if (pattern.matcher(trimmed).matches()) {
                return pattern.pattern();
            }
        }

        return null;
    }

    /**
     * Extract filename from path.
     */
    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Internal decision record.
     */
    private record FilterDecision(boolean shouldFilter, FilterReason reason, String details) {
        static FilterDecision keep() {
            return new FilterDecision(false, null, null);
        }

        static FilterDecision filter(FilterReason reason, String details) {
            return new FilterDecision(true, reason, details);
        }
    }
}

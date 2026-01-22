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
 * <p>
 * Detection methods:
 * - Empty commits (0 LoC)
 * - Merge/Revert commits (message pattern)
 * - Rename-only commits (git -M/-C detection)
 * - Format-only commits (whitespace changes, linter runs)
 * - Mass reformat commits (many files, uniform patterns)
 * - Generated files only (lock files, build outputs)
 * - Trivial message patterns (lint, typo, wip)
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

    /**
     * Revert commit patterns.
     */
    private static final List<Pattern> REVERT_PATTERNS = List.of(
            Pattern.compile("^Revert \".*\".*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^Revert .*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^This reverts commit.*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Format/style-only commit message patterns (for mass reformat detection).
     */
    private static final List<Pattern> FORMAT_COMMIT_PATTERNS = List.of(
            Pattern.compile("^(apply|run)\\s+(code\\s*)?(format|formatter|formatting).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^reformat(ted)?\\s+(all\\s*)?(code|files)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(run|apply)\\s+(black|autopep8|gofmt|rustfmt|clang-format).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(code|style)\\s*cleanup.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^normalize\\s+(line\\s*endings|whitespace|imports).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^organize\\s+imports.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^sort\\s+imports.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^fix\\s+(all\\s*)?lint(er)?\\s*(errors|warnings|issues)?\\s*$", Pattern.CASE_INSENSITIVE)
    );

    // ==================== Thresholds ====================

    /**
     * Commits with fewer lines are suspicious if they have trivial messages.
     */
    private static final int SMALL_COMMIT_THRESHOLD = 5;

    /**
     * Threshold for mass reformat detection: many files with few semantic changes.
     */
    private static final int MASS_REFORMAT_FILE_THRESHOLD = 10;

    /**
     * If average lines per file is below this in a multi-file commit, suspicious for reformat.
     */
    private static final double MASS_REFORMAT_AVG_LINES_THRESHOLD = 5.0;

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
        REVERT_COMMIT,
        RENAME_ONLY,
        FORMAT_ONLY,
        MASS_REFORMAT,
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
        int revertCount = 0;
        int renameCount = 0;
        int formatCount = 0;
        int massReformatCount = 0;
        int generatedCount = 0;
        int trivialCount = 0;

        for (CommitChunkDTO chunk : chunks) {
            FilterDecision decision = evaluateChunk(chunk);

            if (decision.shouldFilter()) {
                filtered.add(new PreFilteredCommit(chunk, decision.reason(), decision.details()));

                switch (decision.reason()) {
                    case EMPTY -> emptyCount++;
                    case MERGE_COMMIT -> mergeCount++;
                    case REVERT_COMMIT -> revertCount++;
                    case RENAME_ONLY -> renameCount++;
                    case FORMAT_ONLY -> formatCount++;
                    case MASS_REFORMAT -> massReformatCount++;
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
                emptyCount,
                mergeCount,
                revertCount,
                renameCount,
                formatCount,
                massReformatCount
        );

        log.info("Pre-filter complete: {} of {} commits will be analyzed. {}", 
                toAnalyze.size(), chunks.size(), summary.toSummary());

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

        // 3. Revert commit
        if (isRevertCommit(chunk.commitMessage())) {
            return FilterDecision.filter(FilterReason.REVERT_COMMIT, "Revert commit");
        }

        // 4. Rename-only commit (all files are renames with no content change)
        if (isRenameOnly(chunk)) {
            return FilterDecision.filter(FilterReason.RENAME_ONLY,
                    "Rename-only: " + chunk.files().size() + " files renamed");
        }

        // 5. Format/whitespace-only commit
        if (isFormatOnly(chunk)) {
            return FilterDecision.filter(FilterReason.FORMAT_ONLY,
                    "Format/whitespace-only changes");
        }

        // 6. Mass reformat commit (many files, uniform small changes, format message)
        if (isMassReformat(chunk)) {
            return FilterDecision.filter(FilterReason.MASS_REFORMAT,
                    "Mass reformat: " + chunk.files().size() + " files with avg " +
                            String.format("%.1f", getAvgLinesPerFile(chunk)) + " lines each");
        }

        // 7. Only generated/lock files modified
        if (hasOnlyGeneratedFiles(chunk.files())) {
            return FilterDecision.filter(FilterReason.GENERATED_FILES_ONLY,
                    "Only generated files: " + String.join(", ", chunk.files()));
        }

        // 8. Trivial commit message pattern
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

    // ==================== Detection Methods ====================

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
     * Check if commit message matches revert patterns.
     */
    private boolean isRevertCommit(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return REVERT_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(message.trim()).matches());
    }

    /**
     * Check if commit is rename-only (files renamed but content unchanged).
     * <p>
     * Detection: If CommitChunkDTO has rename metadata from git -M/-C,
     * and the content changes are minimal (only path changes).
     */
    private boolean isRenameOnly(CommitChunkDTO chunk) {
        // Check if chunk has rename flag (requires CommitChunkDTO to have this field)
        if (chunk.renameDetected() != null && chunk.renameDetected()) {
            // If marked as rename and very few actual code changes
            return chunk.totalLinesChanged() <= 2;
        }
        
        // Heuristic: If message contains "rename" and few line changes
        String message = chunk.commitMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if ((lower.contains("rename") || lower.contains("move")) && chunk.totalLinesChanged() <= 5) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if commit is format/whitespace-only.
     * <p>
     * Detection methods:
     * - CommitChunkDTO has formatOnly flag from git diff -w comparison
     * - Message matches format patterns and changes are small per file
     */
    private boolean isFormatOnly(CommitChunkDTO chunk) {
        // Check if chunk has format-only flag (requires CommitChunkDTO to have this field)
        if (chunk.formatOnly() != null && chunk.formatOnly()) {
            return true;
        }
        
        // Heuristic: format message pattern + small average lines per file
        String message = chunk.commitMessage();
        if (message != null && matchesFormatPattern(message)) {
            // If it's a format commit and average lines per file is low, likely format-only
            double avgLines = getAvgLinesPerFile(chunk);
            if (avgLines < 10.0 && chunk.files().size() > 1) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if commit is a mass reformat (many files, uniform changes).
     * <p>
     * Detection:
     * - Many files (>10)
     * - Low average lines per file (<5)
     * - Message matches format/reformat patterns
     */
    private boolean isMassReformat(CommitChunkDTO chunk) {
        // Check if chunk has mass reformat flag
        if (chunk.massReformatFlag() != null && chunk.massReformatFlag()) {
            return true;
        }
        
        int fileCount = chunk.files() != null ? chunk.files().size() : 0;
        if (fileCount < MASS_REFORMAT_FILE_THRESHOLD) {
            return false;
        }
        
        double avgLines = getAvgLinesPerFile(chunk);
        if (avgLines > MASS_REFORMAT_AVG_LINES_THRESHOLD) {
            return false;
        }
        
        // Must also have a format-related message
        String message = chunk.commitMessage();
        return message != null && matchesFormatPattern(message);
    }

    /**
     * Check if message matches format/reformat patterns.
     */
    private boolean matchesFormatPattern(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return FORMAT_COMMIT_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(message.trim()).matches());
    }

    /**
     * Calculate average lines changed per file.
     */
    private double getAvgLinesPerFile(CommitChunkDTO chunk) {
        int fileCount = chunk.files() != null ? chunk.files().size() : 0;
        if (fileCount == 0) {
            return 0.0;
        }
        return (double) chunk.totalLinesChanged() / fileCount;
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

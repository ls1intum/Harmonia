package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService.FilterReason;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService.PreFilterResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CommitPreFilterService.
 * Tests filtering of commits before LLM analysis.
 */
class CommitPreFilterServiceTest {

    private CommitPreFilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new CommitPreFilterService();
    }

    // ==================== Empty Commit Tests ====================

    @Test
    void testFilterEmptyCommit() {
        CommitChunkDTO emptyCommit = createChunk("abc123", "feat: add feature", 0, 0);

        PreFilterResult result = filterService.preFilter(List.of(emptyCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(1, result.filteredChunks().size());
        assertEquals(FilterReason.EMPTY, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().emptyCount());
    }

    // ==================== Merge Commit Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "Merge branch 'feature' into main",
            "Merge pull request #123 from user/branch",
            "Merge remote-tracking branch 'origin/main'",
            "Merge 'develop' into main",
            "Merged feature branch"
    })
    void testFilterMergeCommits(String message) {
        CommitChunkDTO mergeCommit = createChunk("abc123", message, 10, 5);

        PreFilterResult result = filterService.preFilter(List.of(mergeCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(1, result.filteredChunks().size());
        assertEquals(FilterReason.MERGE_COMMIT, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().mergeCount());
    }

    // ==================== Revert Commit Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "Revert \"Add new feature\"",
            "Revert previous commit",
            "This reverts commit abc123"
    })
    void testFilterRevertCommits(String message) {
        CommitChunkDTO revertCommit = createChunk("abc123", message, 20, 20);

        PreFilterResult result = filterService.preFilter(List.of(revertCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.REVERT_COMMIT, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().revertCount());
    }

    // ==================== Trivial Message Tests ====================

    @ParameterizedTest
    @ValueSource(strings = {
            // Linting
            "fix lint",
            "linting",
            "run prettier",
            "apply eslint",
            "checkstyle",
            "spotless",
            // Formatting
            "format",
            "formatting",
            "code formatting",
            "fix formatting",
            "apply format",
            // Whitespace
            "fix whitespace",
            "whitespace",
            "fix indentation",
            // Style
            "style: fix imports",
            // Typos
            "fix typo",
            "typos",
            "correct typo",
            // WIP
            "wip",
            "WIP: working on feature",
            "temp",
            "test",
            // Meaningless
            ".",
            "..",
            "...",
            "oops",
            "stuff",
            "changes",
            // Auto-generated
            "auto-format",
            "update dependencies",
            "[bot] update",
            "chore(deps): bump version",
            // Initial
            "initial commit",
            "first commit",
            "init"
    })
    void testFilterTrivialMessages(String message) {
        CommitChunkDTO trivialCommit = createChunk("abc123", message, 3, 2);

        PreFilterResult result = filterService.preFilter(List.of(trivialCommit));

        assertEquals(0, result.chunksToAnalyze().size(),
                "Message should be filtered: " + message);
        assertTrue(result.filteredChunks().get(0).reason() == FilterReason.TRIVIAL_MESSAGE
                || result.filteredChunks().get(0).reason() == FilterReason.SMALL_TRIVIAL_COMMIT,
                "Expected TRIVIAL_MESSAGE or SMALL_TRIVIAL_COMMIT for: " + message);
    }

    // ==================== Generated Files Tests ====================

    @Test
    void testFilterGeneratedFilesOnly() {
        CommitChunkDTO lockFileCommit = createChunkWithFiles(
                "abc123", "update deps", 100, 50,
                List.of("package-lock.json")
        );

        PreFilterResult result = filterService.preFilter(List.of(lockFileCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.GENERATED_FILES_ONLY, result.filteredChunks().get(0).reason());
    }

    @Test
    void testFilterMultipleGeneratedFiles() {
        CommitChunkDTO multiLockCommit = createChunkWithFiles(
                "abc123", "update all deps", 500, 300,
                List.of("package-lock.json", "yarn.lock", "Cargo.lock")
        );

        PreFilterResult result = filterService.preFilter(List.of(multiLockCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.GENERATED_FILES_ONLY, result.filteredChunks().get(0).reason());
    }

    @Test
    void testKeepMixedFilesCommit() {
        // Has both generated and real files - should NOT be filtered
        CommitChunkDTO mixedCommit = createChunkWithFiles(
                "abc123", "feat: add feature with deps", 100, 50,
                List.of("package-lock.json", "src/main/App.java")
        );

        PreFilterResult result = filterService.preFilter(List.of(mixedCommit));

        assertEquals(1, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
    }

    // ==================== Rename Detection Tests ====================

    @Test
    void testFilterRenameOnlyWithFlag() {
        CommitChunkDTO renameCommit = createChunkWithFlags(
                "abc123", "refactor: rename file", 1, 1,
                true, false, false  // renameDetected=true
        );

        PreFilterResult result = filterService.preFilter(List.of(renameCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.RENAME_ONLY, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().renameOnlyCount());
    }

    @Test
    void testFilterRenameByMessage() {
        CommitChunkDTO renameCommit = createChunk("abc123", "rename UserService to UserManager", 3, 2);

        PreFilterResult result = filterService.preFilter(List.of(renameCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.RENAME_ONLY, result.filteredChunks().get(0).reason());
    }

    // ==================== Format-Only Detection Tests ====================

    @Test
    void testFilterFormatOnlyWithFlag() {
        CommitChunkDTO formatCommit = createChunkWithFlags(
                "abc123", "apply prettier", 50, 50,
                false, true, false  // formatOnly=true
        );

        PreFilterResult result = filterService.preFilter(List.of(formatCommit));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.FORMAT_ONLY, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().formatOnlyCount());
    }

    // ==================== Mass Reformat Detection Tests ====================

    @Test
    void testFilterMassReformatWithFlag() {
        CommitChunkDTO massReformat = createChunkWithFlags(
                "abc123", "run black formatter", 30, 20,
                false, false, true  // massReformatFlag=true
        );

        PreFilterResult result = filterService.preFilter(List.of(massReformat));

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(FilterReason.MASS_REFORMAT, result.filteredChunks().get(0).reason());
        assertEquals(1, result.summary().massReformatCount());
    }

    @Test
    void testFilterMassReformatByHeuristic() {
        // 15 files, 30 total lines = 2 lines per file average, with format message
        // Note: Could be caught by FORMAT_ONLY or MASS_REFORMAT depending on order
        CommitChunkDTO massReformat = createChunkWithFiles(
                "abc123", "reformat all code", 15, 15,
                List.of("a.java", "b.java", "c.java", "d.java", "e.java",
                        "f.java", "g.java", "h.java", "i.java", "j.java",
                        "k.java", "l.java", "m.java", "n.java", "o.java")
        );

        PreFilterResult result = filterService.preFilter(List.of(massReformat));

        assertEquals(0, result.chunksToAnalyze().size());
        // Either FORMAT_ONLY or MASS_REFORMAT is acceptable for this pattern
        FilterReason reason = result.filteredChunks().get(0).reason();
        assertTrue(reason == FilterReason.MASS_REFORMAT || reason == FilterReason.FORMAT_ONLY,
                "Expected FORMAT_ONLY or MASS_REFORMAT, got: " + reason);
    }

    // ==================== Keep Valid Commits Tests ====================

    @Test
    void testKeepValidFeatureCommit() {
        CommitChunkDTO featureCommit = createChunk("abc123", "feat: implement user authentication", 150, 20);

        PreFilterResult result = filterService.preFilter(List.of(featureCommit));

        assertEquals(1, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
        assertEquals(1, result.summary().productiveCommits());
    }

    @Test
    void testKeepValidBugfixCommit() {
        CommitChunkDTO bugfixCommit = createChunk("abc123", "fix: resolve null pointer in UserService", 30, 10);

        PreFilterResult result = filterService.preFilter(List.of(bugfixCommit));

        assertEquals(1, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
    }

    @Test
    void testKeepRefactoringCommit() {
        CommitChunkDTO refactorCommit = createChunk("abc123", "refactor: extract payment logic to separate service", 200, 150);

        PreFilterResult result = filterService.preFilter(List.of(refactorCommit));

        assertEquals(1, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
    }

    // ==================== Mixed Batch Tests ====================

    @Test
    void testMixedBatch() {
        List<CommitChunkDTO> commits = List.of(
                createChunk("1", "Merge branch 'main'", 10, 5),           // MERGE
                createChunk("2", "feat: add login", 100, 20),             // KEEP
                createChunk("3", "", 0, 0),                               // EMPTY
                createChunk("4", "fix lint", 3, 2),                       // TRIVIAL
                createChunk("5", "fix: resolve bug in parser", 50, 10),   // KEEP
                createChunk("6", "wip", 5, 5),                            // TRIVIAL
                createChunk("7", "Revert \"bad commit\"", 30, 30)         // REVERT
        );

        PreFilterResult result = filterService.preFilter(commits);

        assertEquals(2, result.chunksToAnalyze().size());
        assertEquals(5, result.filteredChunks().size());

        // Verify kept commits
        assertTrue(result.chunksToAnalyze().stream()
                .anyMatch(c -> c.commitSha().equals("2")));
        assertTrue(result.chunksToAnalyze().stream()
                .anyMatch(c -> c.commitSha().equals("5")));
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyList() {
        PreFilterResult result = filterService.preFilter(List.of());

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
        assertEquals(0, result.summary().totalCommits());
    }

    @Test
    void testNullList() {
        PreFilterResult result = filterService.preFilter(null);

        assertEquals(0, result.chunksToAnalyze().size());
        assertEquals(0, result.filteredChunks().size());
    }

    @Test
    void testSummaryPercentage() {
        List<CommitChunkDTO> commits = List.of(
                createChunk("1", "Merge branch", 10, 5),
                createChunk("2", "feat: add feature", 100, 20),
                createChunk("3", "fix lint", 3, 2),
                createChunk("4", "fix: bug", 50, 10)
        );

        PreFilterResult result = filterService.preFilter(commits);

        assertEquals(4, result.summary().totalCommits());
        assertEquals(2, result.summary().productiveCommits());
        assertEquals(50.0, result.summary().getFilteredPercentage(), 0.1);
    }

    // ==================== Helper Methods ====================

    private CommitChunkDTO createChunk(String sha, String message, int added, int deleted) {
        return new CommitChunkDTO(
                sha, 1L, "test@example.com", message,
                LocalDateTime.now(), List.of("src/Main.java"), "diff content",
                added, deleted, 0, 1, false, List.of(),
                null, null, null
        );
    }

    private CommitChunkDTO createChunkWithFiles(String sha, String message, int added, int deleted, List<String> files) {
        return new CommitChunkDTO(
                sha, 1L, "test@example.com", message,
                LocalDateTime.now(), files, "diff content",
                added, deleted, 0, 1, false, List.of(),
                null, null, null
        );
    }

    private CommitChunkDTO createChunkWithFlags(String sha, String message, int added, int deleted,
                                                 Boolean renameDetected, Boolean formatOnly, Boolean massReformatFlag) {
        return new CommitChunkDTO(
                sha, 1L, "test@example.com", message,
                LocalDateTime.now(), List.of("src/Main.java"), "diff content",
                added, deleted, 0, 1, false, List.of(),
                renameDetected, formatOnly, massReformatFlag
        );
    }
}

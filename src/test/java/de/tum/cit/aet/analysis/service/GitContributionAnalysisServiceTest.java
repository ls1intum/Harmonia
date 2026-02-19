package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.service.GitContributionAnalysisService.FullCommitMappingResult;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GitContributionAnalysisService#buildFullCommitMap}.
 * Uses a real temporary Git repo created with JGit.
 *
 * <p>Scenario: Two students push to a shared repo. Artemis VCS logs only record
 * the HEAD commit of each push. Intermediate commits must be discovered by
 * walking the full git history.</p>
 *
 * <pre>
 * Template commit (template@artemis.de) — before project start
 * Commit 1 (studentA@tum.de) — Student A
 * Commit 2 (studentA@tum.de) — Student A
 * Commit 3 (studentA@tum.de) — Student A  ← VCS log records this (Push 1 HEAD)
 * Commit 4 (bob@gmail.com)   — Student B (misconfigured git email)
 * Commit 5 (bob@gmail.com)   — Student B  ← VCS log records this (Push 2 HEAD)
 * </pre>
 */
class GitContributionAnalysisServiceTest {

    @TempDir
    Path tempDir;

    private final GitContributionAnalysisService service = new GitContributionAnalysisService();

    private static final long STUDENT_A_ID = 100L;
    private static final long STUDENT_B_ID = 200L;
    private static final String STUDENT_A_EMAIL = "studentA@tum.de";
    private static final String STUDENT_B_EMAIL = "studentB@tum.de";
    private static final String BOB_GIT_EMAIL = "bob@gmail.com";
    private static final String TEMPLATE_EMAIL = "template@artemis.de";

    // Commit hashes filled during setup
    private String templateHash;
    private String commit1Hash;
    private String commit2Hash;
    private String commit3Hash;
    private String commit4Hash;
    private String commit5Hash;

    @BeforeEach
    void setUp() throws Exception {
        File repoDir = tempDir.toFile();

        try (Git git = Git.init().setDirectory(repoDir).call()) {
            // Template commit (before project start)
            writeFile("README.md", "# Template Project\n");
            git.add().addFilepattern("README.md").call();
            RevCommit template = git.commit()
                    .setAuthor(new PersonIdent("Template", TEMPLATE_EMAIL))
                    .setMessage("Initial template")
                    .call();
            templateHash = template.getName();

            // Commit 1 — Student A
            writeFile("src/Main.java", "public class Main {}\n");
            git.add().addFilepattern("src/Main.java").call();
            RevCommit c1 = git.commit()
                    .setAuthor(new PersonIdent("Student A", STUDENT_A_EMAIL))
                    .setMessage("Add Main class")
                    .call();
            commit1Hash = c1.getName();

            // Commit 2 — Student A
            writeFile("src/Main.java", "public class Main { void run() {} }\n");
            git.add().addFilepattern("src/Main.java").call();
            RevCommit c2 = git.commit()
                    .setAuthor(new PersonIdent("Student A", STUDENT_A_EMAIL))
                    .setMessage("Add run method")
                    .call();
            commit2Hash = c2.getName();

            // Commit 3 — Student A (HEAD of Push 1)
            writeFile("src/Helper.java", "public class Helper {}\n");
            git.add().addFilepattern("src/Helper.java").call();
            RevCommit c3 = git.commit()
                    .setAuthor(new PersonIdent("Student A", STUDENT_A_EMAIL))
                    .setMessage("Add Helper class")
                    .call();
            commit3Hash = c3.getName();

            // Commit 4 — Student B with misconfigured git email
            writeFile("src/Utils.java", "public class Utils {}\n");
            git.add().addFilepattern("src/Utils.java").call();
            RevCommit c4 = git.commit()
                    .setAuthor(new PersonIdent("Bob", BOB_GIT_EMAIL))
                    .setMessage("Add Utils class")
                    .call();
            commit4Hash = c4.getName();

            // Commit 5 — Student B with misconfigured git email (HEAD of Push 2)
            writeFile("src/Utils.java", "public class Utils { static void help() {} }\n");
            git.add().addFilepattern("src/Utils.java").call();
            RevCommit c5 = git.commit()
                    .setAuthor(new PersonIdent("Bob", BOB_GIT_EMAIL))
                    .setMessage("Add help method")
                    .call();
            commit5Hash = c5.getName();
        }
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path filePath = tempDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }

    private TeamRepositoryDTO buildRepo(List<VCSLogDTO> vcsLogs) {
        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL));

        TeamDTO team = new TeamDTO(1L, "Team Alpha", "TA", students, null);
        ParticipationDTO participation = new ParticipationDTO(team, 1L, "https://repo.example.com", 2);

        return new TeamRepositoryDTO(participation, vcsLogs, tempDir.toString(), true, null);
    }

    private List<VCSLogDTO> defaultVcsLogs() {
        return List.of(
                new VCSLogDTO(STUDENT_A_EMAIL, "PUSH", commit3Hash),
                new VCSLogDTO(STUDENT_B_EMAIL, "PUSH", commit5Hash));
    }

    @Test
    void testAllCommitsDiscovered() {
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // All 5 student commits + template = 6 total
        int totalCommits = result.commitToAuthor().size() + result.orphanCommitEmails().size();
        assertEquals(6, totalCommits, "Should discover all 6 reachable commits");

        // All 5 student commits should be assigned (not just 2 from VCS log)
        assertTrue(result.commitToAuthor().containsKey(commit1Hash), "Commit 1 should be assigned");
        assertTrue(result.commitToAuthor().containsKey(commit2Hash), "Commit 2 should be assigned");
        assertTrue(result.commitToAuthor().containsKey(commit3Hash), "Commit 3 should be assigned");
        assertTrue(result.commitToAuthor().containsKey(commit4Hash), "Commit 4 should be assigned");
        assertTrue(result.commitToAuthor().containsKey(commit5Hash), "Commit 5 should be assigned");
    }

    @Test
    void testVcsAnchorEmailPreferred() {
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Commit 3 VCS anchor -> Student A
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit3Hash));
        // Commit 5 VCS anchor -> Student B
        assertEquals(STUDENT_B_ID, result.commitToAuthor().get(commit5Hash));

        // VCS emails should be recorded for anchor commits
        assertEquals(STUDENT_A_EMAIL, result.commitToVcsEmail().get(commit3Hash));
        assertEquals(STUDENT_B_EMAIL, result.commitToVcsEmail().get(commit5Hash));
    }

    @Test
    void testLearnedMappingForMisconfiguredEmail() {
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Commit 4 has git email bob@gmail.com which doesn't match any student directly.
        // But Commit 5 (VCS anchor) also has bob@gmail.com and maps to Student B.
        // The learned mapping bob@gmail.com -> Student B should assign Commit 4 to Student B.
        assertEquals(STUDENT_B_ID, result.commitToAuthor().get(commit4Hash),
                "Commit 4 should be assigned to Student B via learned mapping");
    }

    @Test
    void testDirectEmailMatch() {
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Commits 1, 2 have git email studentA@tum.de which matches Student A directly
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit1Hash),
                "Commit 1 should be assigned to Student A via direct email match");
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit2Hash),
                "Commit 2 should be assigned to Student A via direct email match");
    }

    @Test
    void testTemplateCommitIsOrphan() {
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Template commit email doesn't match any student
        assertTrue(result.orphanCommitEmails().containsKey(templateHash),
                "Template commit should be an orphan");
        assertFalse(result.commitToAuthor().containsKey(templateHash),
                "Template commit should not be assigned to any student");
    }

    @Test
    void testEmptyVcsLogFallback() {
        // No VCS logs at all — only direct email matching is possible
        TeamRepositoryDTO repo = buildRepo(List.of());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Commits 1, 2, 3 have studentA@tum.de -> direct match to Student A
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit1Hash));
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit2Hash));
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit3Hash));

        // Commits 4, 5 have bob@gmail.com -> no match (no learned mapping without VCS logs)
        assertTrue(result.orphanCommitEmails().containsKey(commit4Hash),
                "Commit 4 should be orphan without VCS logs (bob@gmail.com doesn't match)");
        assertTrue(result.orphanCommitEmails().containsKey(commit5Hash),
                "Commit 5 should be orphan without VCS logs (bob@gmail.com doesn't match)");

        // Template commit -> orphan
        assertTrue(result.orphanCommitEmails().containsKey(templateHash));
    }

    @Test
    void testEmailConflict_twoStudentsSameGitEmail() throws Exception {
        // Both students use the same git email "shared@gmail.com" in their commits.
        // VCS anchors map shared@gmail.com to Student A first (commit3).
        // putIfAbsent keeps Student A; Student B's anchor (commit5) does not overwrite.
        // So commit4 (also shared@gmail.com) is attributed to Student A via learned mapping.
        Path conflictDir = tempDir.resolve("conflict-repo");
        Files.createDirectories(conflictDir);

        String hash3, hash4, hash5;
        try (Git git = Git.init().setDirectory(conflictDir.toFile()).call()) {
            Path readme = conflictDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setAuthor(new PersonIdent("X", "x@example.com")).setMessage("init").call();

            // Commit 3 — pushed by Student A but git email is shared@gmail.com
            Files.writeString(readme, "change A\n");
            git.add().addFilepattern("README.md").call();
            hash3 = git.commit()
                    .setAuthor(new PersonIdent("Shared", "shared@gmail.com"))
                    .setMessage("A work").call().getName();

            // Commit 4 — intermediate by Student B, same shared email
            Files.writeString(readme, "change B\n");
            git.add().addFilepattern("README.md").call();
            hash4 = git.commit()
                    .setAuthor(new PersonIdent("Shared", "shared@gmail.com"))
                    .setMessage("B work").call().getName();

            // Commit 5 — pushed by Student B, same shared email
            Files.writeString(readme, "change B2\n");
            git.add().addFilepattern("README.md").call();
            hash5 = git.commit()
                    .setAuthor(new PersonIdent("Shared", "shared@gmail.com"))
                    .setMessage("B work 2").call().getName();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL));

        // VCS anchors: commit3 -> Student A, commit5 -> Student B
        List<VCSLogDTO> vcsLogs = List.of(
                new VCSLogDTO(STUDENT_A_EMAIL, "PUSH", hash3),
                new VCSLogDTO(STUDENT_B_EMAIL, "PUSH", hash5));

        FullCommitMappingResult result = service.buildFullCommitMap(
                conflictDir.toString(), vcsLogs, students);

        // commit3 is VCS anchor -> Student A (tier 1)
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(hash3));

        // commit5 is VCS anchor -> Student B (tier 1)
        assertEquals(STUDENT_B_ID, result.commitToAuthor().get(hash5));

        // commit4: RevWalk visits newest-first, so commit5 (Student B) is the
        // first anchor seen for shared@gmail.com. putIfAbsent keeps Student B.
        assertEquals(STUDENT_B_ID, result.commitToAuthor().get(hash4),
                "Conflict email should map to the first-seen student (putIfAbsent)");
    }

    @Test
    void testNullAuthorEmail() throws Exception {
        // Commit with empty author email should not crash; it becomes an orphan.
        Path nullEmailDir = tempDir.resolve("null-email-repo");
        Files.createDirectories(nullEmailDir);

        try (Git git = Git.init().setDirectory(nullEmailDir.toFile()).call()) {
            Path readme = nullEmailDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setAuthor(new PersonIdent("NoEmail", "")).setMessage("no email commit").call();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL));

        FullCommitMappingResult result = service.buildFullCommitMap(
                nullEmailDir.toString(), List.of(), students);

        // Should not crash. The commit has no matching email, so it's an orphan.
        int total = result.commitToAuthor().size() + result.orphanCommitEmails().size();
        assertEquals(1, total, "Single commit should be discovered");
        assertEquals(1, result.orphanCommitEmails().size(),
                "Commit with empty email should be orphaned");
    }

    @Test
    void testEmptyRepository() throws Exception {
        // Fresh git init with no commits. Should return empty, no crash.
        Path emptyDir = tempDir.resolve("empty-repo");
        Files.createDirectories(emptyDir);

        try (Git git = Git.init().setDirectory(emptyDir.toFile()).call()) {
            // No commits
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL));

        FullCommitMappingResult result = service.buildFullCommitMap(
                emptyDir.toString(), List.of(), students);

        assertTrue(result.commitToAuthor().isEmpty());
        assertTrue(result.orphanCommitEmails().isEmpty());
    }

    @Test
    void testAllCommitsAreOrphans() throws Exception {
        // Repo has commits but none match any student email or VCS log.
        Path orphanDir = tempDir.resolve("orphan-repo");
        Files.createDirectories(orphanDir);

        try (Git git = Git.init().setDirectory(orphanDir.toFile()).call()) {
            Path readme = orphanDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setAuthor(new PersonIdent("Unknown", "unknown@other.com"))
                    .setMessage("unknown work").call();

            Files.writeString(readme, "update\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setAuthor(new PersonIdent("Stranger", "stranger@other.com"))
                    .setMessage("stranger work").call();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL));

        FullCommitMappingResult result = service.buildFullCommitMap(
                orphanDir.toString(), List.of(), students);

        assertTrue(result.commitToAuthor().isEmpty(), "No commits should match any student");
        assertEquals(2, result.orphanCommitEmails().size(), "Both commits should be orphans");
    }

    @Test
    void testStudentWithNoCommits() {
        // Three students in team but only Student A and B have commits. Student C has none.
        long studentCId = 300L;
        String studentCEmail = "studentC@tum.de";

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL),
                new ParticipantDTO(studentCId, "studentC", "Student C", studentCEmail));

        TeamDTO team = new TeamDTO(1L, "Team Alpha", "TA", students, null);
        ParticipationDTO participation = new ParticipationDTO(team, 1L, "https://repo.example.com", 3);
        TeamRepositoryDTO repo = new TeamRepositoryDTO(participation, defaultVcsLogs(), tempDir.toString(), true, null);

        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        // Student A and B should have commits assigned
        assertTrue(result.commitToAuthor().containsValue(STUDENT_A_ID));
        assertTrue(result.commitToAuthor().containsValue(STUDENT_B_ID));

        // Student C should simply not appear — no error
        assertFalse(result.commitToAuthor().containsValue(studentCId),
                "Student C with no commits should not appear in commitToAuthor");
    }

    @Test
    void testNullLocalPathReturnsEmpty() {
        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL));
        TeamDTO team = new TeamDTO(1L, "Team Alpha", "TA", students, null);
        ParticipationDTO participation = new ParticipationDTO(team, 1L, null, 0);
        TeamRepositoryDTO repo = new TeamRepositoryDTO(participation, List.of(), null, false, null);

        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        assertTrue(result.commitToAuthor().isEmpty());
        assertTrue(result.orphanCommitEmails().isEmpty());
    }

    @Test
    void testCaseInsensitiveEmailMatching() throws Exception {
        // Git email is uppercase; Artemis email is lowercase.
        // Tier 3 direct match should still work.
        Path caseDir = tempDir.resolve("case-repo");
        Files.createDirectories(caseDir);

        String commitHash;
        try (Git git = Git.init().setDirectory(caseDir.toFile()).call()) {
            Path readme = caseDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            commitHash = git.commit()
                    .setAuthor(new PersonIdent("Student A", "StudentA@TUM.DE"))
                    .setMessage("uppercase email commit")
                    .call().getName();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", "studenta@tum.de"));

        FullCommitMappingResult result = service.buildFullCommitMap(
                caseDir.toString(), List.of(), students);

        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commitHash),
                "Tier 3 direct match should be case-insensitive");
    }

    @Test
    void testVcsAnchorExternalDoesNotFallThrough() throws Exception {
        // VCS log says commit was pushed by external@other.com (not a team member).
        // Git author email on that commit is studentA@tum.de (matches a team member).
        // The commit should be an orphan, NOT attributed to Student A.
        Path anchorDir = tempDir.resolve("anchor-external-repo");
        Files.createDirectories(anchorDir);

        String commitHash;
        try (Git git = Git.init().setDirectory(anchorDir.toFile()).call()) {
            Path readme = anchorDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            commitHash = git.commit()
                    .setAuthor(new PersonIdent("Student A", STUDENT_A_EMAIL))
                    .setMessage("pushed by external")
                    .call().getName();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL));

        // VCS log records this commit as pushed by an external email
        List<VCSLogDTO> vcsLogs = List.of(
                new VCSLogDTO("external@other.com", "PUSH", commitHash));

        FullCommitMappingResult result = service.buildFullCommitMap(
                anchorDir.toString(), vcsLogs, students);

        assertFalse(result.commitToAuthor().containsKey(commitHash),
                "VCS anchor with external email should NOT fall through to Tier 2/3");
        assertTrue(result.orphanCommitEmails().containsKey(commitHash),
                "Commit should be an orphan when VCS anchor email is external");
    }

    @Test
    void testVcsLogOverlap_disambiguatedByGitAuthor() throws Exception {
        // Both students push the same merge commit, creating two VCS log entries.
        // Git author email matches Student A -> should be assigned to Student A.
        Path overlapDir = tempDir.resolve("overlap-repo");
        Files.createDirectories(overlapDir);

        String mergeHash;
        try (Git git = Git.init().setDirectory(overlapDir.toFile()).call()) {
            Path readme = overlapDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            mergeHash = git.commit()
                    .setAuthor(new PersonIdent("Student A", STUDENT_A_EMAIL))
                    .setMessage("merge commit")
                    .call().getName();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL));

        // Two VCS log entries for the same commit hash
        List<VCSLogDTO> vcsLogs = List.of(
                new VCSLogDTO(STUDENT_A_EMAIL, "PUSH", mergeHash),
                new VCSLogDTO(STUDENT_B_EMAIL, "PUSH", mergeHash));

        FullCommitMappingResult result = service.buildFullCommitMap(
                overlapDir.toString(), vcsLogs, students);

        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(mergeHash),
                "Overlap should be resolved to Student A via git-author email");
        assertEquals(STUDENT_A_EMAIL, result.commitToVcsEmail().get(mergeHash),
                "Display email should be Student A's VCS email");
    }

    @Test
    void testVcsLogOverlap_unknownGitAuthor_fallsBackToFirst() throws Exception {
        // Both students push the same commit, but git author email is unknown.
        // Should fall back to the first VCS log entry.
        Path overlapDir = tempDir.resolve("overlap-unknown-repo");
        Files.createDirectories(overlapDir);

        String mergeHash;
        try (Git git = Git.init().setDirectory(overlapDir.toFile()).call()) {
            Path readme = overlapDir.resolve("README.md");
            Files.writeString(readme, "init\n");
            git.add().addFilepattern("README.md").call();
            mergeHash = git.commit()
                    .setAuthor(new PersonIdent("Unknown", "unknown@random.com"))
                    .setMessage("merge commit")
                    .call().getName();
        }

        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(STUDENT_A_ID, "studentA", "Student A", STUDENT_A_EMAIL),
                new ParticipantDTO(STUDENT_B_ID, "studentB", "Student B", STUDENT_B_EMAIL));

        List<VCSLogDTO> vcsLogs = List.of(
                new VCSLogDTO(STUDENT_A_EMAIL, "PUSH", mergeHash),
                new VCSLogDTO(STUDENT_B_EMAIL, "PUSH", mergeHash));

        FullCommitMappingResult result = service.buildFullCommitMap(
                overlapDir.toString(), vcsLogs, students);

        // Should not be orphan — assigned to first VCS entry's student
        assertTrue(result.commitToAuthor().containsKey(mergeHash),
                "Overlap with unknown git author should not produce an orphan");
        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(mergeHash),
                "Should fall back to first VCS email when git author is unknown");
    }

    @Test
    void testTier2CommitHasDisplayEmail() {
        // Commit 4 is assigned via Tier 2 (learned mapping: bob@gmail.com -> Student B).
        // commitToVcsEmail should contain Student B's Artemis email for this commit.
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        assertEquals(STUDENT_B_ID, result.commitToAuthor().get(commit4Hash),
                "Commit 4 should be assigned to Student B via learned mapping");
        assertEquals(STUDENT_B_EMAIL, result.commitToVcsEmail().get(commit4Hash),
                "Tier 2 commit should have Student B's Artemis email as display email");
    }

    @Test
    void testTier3CommitHasDisplayEmail() {
        // Commits 1 and 2 are assigned via Tier 3 (direct match: studentA@tum.de).
        // commitToVcsEmail should contain Student A's Artemis email for these commits.
        TeamRepositoryDTO repo = buildRepo(defaultVcsLogs());
        FullCommitMappingResult result = service.buildFullCommitMap(repo);

        assertEquals(STUDENT_A_ID, result.commitToAuthor().get(commit1Hash));
        assertEquals(STUDENT_A_EMAIL, result.commitToVcsEmail().get(commit1Hash),
                "Tier 3 commit should have Student A's Artemis email as display email");
        assertEquals(STUDENT_A_EMAIL, result.commitToVcsEmail().get(commit2Hash),
                "Tier 3 commit should have Student A's Artemis email as display email");
    }
}

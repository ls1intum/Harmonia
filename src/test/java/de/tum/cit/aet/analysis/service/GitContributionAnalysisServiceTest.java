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
        int totalCommits = result.commitToAuthor().size() + result.orphanCommitHashes().size();
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
        assertTrue(result.orphanCommitHashes().contains(templateHash),
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
        assertTrue(result.orphanCommitHashes().contains(commit4Hash),
                "Commit 4 should be orphan without VCS logs (bob@gmail.com doesn't match)");
        assertTrue(result.orphanCommitHashes().contains(commit5Hash),
                "Commit 5 should be orphan without VCS logs (bob@gmail.com doesn't match)");

        // Template commit -> orphan
        assertTrue(result.orphanCommitHashes().contains(templateHash));
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
        assertTrue(result.orphanCommitHashes().isEmpty());
    }
}

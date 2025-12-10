package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
@Slf4j
public class GitContributionAnalysisService {

    public final Map<Long, AuthorContributionDTO> authorContributions = new HashMap<>();

    /**
     * Maps each commit hash to the corresponding author ID based on the VCS logs and team participation data.
     *
     * @param repo The TeamRepositoryDTO containing participation and logs.
     * @return A map from commit hash (String) to Author ID (Long).
     */
    private Map<String, Long> mapCommitToAuthor(TeamRepositoryDTO repo) {
        Map<String, Long> commitToStudent = new HashMap<>();
        Map<String, Long> emailToStudent = new HashMap<>();
        repo.participation().team().students().forEach(student -> emailToStudent.put(student.email(), student.id()));
        for (VCSLogDTO log : repo.vcsLogs()) {
            commitToStudent.put(log.commitHash(), emailToStudent.get(log.email()));

        }
        return commitToStudent;
    }

    /**
     * Analyzes the Git repository at the given local path and updates the author contributions map.
     *
     * @param localPath      The local file system path to the Git repository.
     * @param commitToAuthor A map from commit hash to author ID.
     * @throws IOException If an error occurs while accessing the repository.
     */
    public void analyzeRepositoryContributions(String localPath, Map<String, Long> commitToAuthor) throws IOException {
        log.info("Running git analysis on local path: {}", localPath);

        // Initialize the JGit Repository object using the .git directory.
        File gitDir = new File(localPath, ".git");

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            // Use RevWalk for commit traversal and DiffFormatter to calculate line changes.
            try (RevWalk revWalk = new RevWalk(repository);
                 DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

                df.setRepository(repository);
                // Enable rename detection for more accurate tracking.
                df.setDetectRenames(true);

                for (Map.Entry<String, Long> entry : commitToAuthor.entrySet()) {
                    String commitHash = entry.getKey();
                    Long authorId = entry.getValue();

                    // Resolve the hash string to JGit's internal ObjectId.
                    ObjectId commitId = repository.resolve(commitHash);
                    if (commitId == null) {
                        log.warn("Unable to resolve commit {}", commitHash);
                        continue;
                    }

                    RevCommit commit = revWalk.parseCommit(commitId);

                    // Find the parent commit (the 'before' state) for calculating the diff.
                    RevCommit oldCommit = (commit.getParentCount() > 0)
                            ? revWalk.parseCommit(commit.getParent(0).getId())
                            : null;

                    int linesAdded = 0;
                    int linesDeleted = 0;

                    // Scan the difference between the parent and the current commit.
                    List<DiffEntry> diffs = df.scan(oldCommit, commit);

                    for (DiffEntry diff : diffs) {
                        FileHeader fh = df.toFileHeader(diff);
                        for (Edit edit : fh.toEditList()) {
                            linesAdded += edit.getLengthB();
                            linesDeleted += edit.getLengthA();
                        }
                    }

                    // Update the total contributions for the author.
                    // Assumes authorContributions is a Map<Long, int[]> with format [linesAdded, linesRemoved, commitCount].
                    AuthorContributionDTO currentContributions = authorContributions.getOrDefault(authorId, new AuthorContributionDTO(0, 0, 0));
                    authorContributions.put(authorId, new AuthorContributionDTO(
                            currentContributions.linesAdded() + linesAdded,
                            currentContributions.linesDeleted() + linesDeleted,
                            currentContributions.commitCount() + 1));

                    log.info("Student ID {}: +{} -{} lines for commit {}", authorId, linesAdded, linesDeleted, commitHash);
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing repository at " + localPath, e);
        }
    }

    /**
     * Processes all team repositories to analyze contributions.
     *
     * @param teamRepositories List of TeamRepositoryDTO to process.
     * @return A map from author ID to their contribution statistics.
     */
    public Map<Long, AuthorContributionDTO> processAllRepositories(List<TeamRepositoryDTO> teamRepositories) {
        authorContributions.clear();

        for (TeamRepositoryDTO repo : teamRepositories) {
            // Map commits to student emails (based on logs)
            Map<String, Long> commitToAuthor = mapCommitToAuthor(repo);

            // Iterate and analyze each repository
            String localPath = repo.localPath();
            try {
                analyzeRepositoryContributions(localPath, commitToAuthor);
            } catch (IOException e) {
                log.error("Error processing repository {}: {}", repo.participation().repositoryUri(), e.getMessage());
            }
        }
        return authorContributions;
    }
}

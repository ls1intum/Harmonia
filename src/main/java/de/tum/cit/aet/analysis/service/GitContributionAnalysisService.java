package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.repositoryProcessing.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
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

    public final Map<Long, int[]> authorContributions = new HashMap<>();

    /**
     * Maps commit hashes from VCS logs to the corresponding participant's email.
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
     * Calculates the total lines added and removed for each author in a single repository
     * by running the 'git show --numstat' command for each commit.
     *
     * @param localPath      The local path to the repository on the file system.
     * @param commitToAuthor A map from commit hash (String) to Author ID (Long).
     * @throws IOException If an I/O error occurs during process execution.
     */
    public void analyzeRepositoryContributions(String localPath, Map<String, Long> commitToAuthor) throws IOException {
        System.out.println("Running git analysis on local path: " + localPath);

        File gitDir = new File(localPath, ".git");
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            try (ObjectReader reader = repository.newObjectReader();
                 RevWalk revWalk = new RevWalk(repository)) {

                for (Map.Entry<String, Long> entry : commitToAuthor.entrySet()) {
                    String commitHash = entry.getKey();
                    Long authorId = entry.getValue();

                    ObjectId commitId = repository.resolve(commitHash);
                    if (commitId == null) {
                        System.err.println("Unable to resolve commit " + commitHash);
                        continue;
                    }

                    RevCommit commit = revWalk.parseCommit(commitId);

                    CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                    newTreeIter.reset(reader, commit.getTree().getId());

                    org.eclipse.jgit.treewalk.AbstractTreeIterator oldTreeIter;
                    if (commit.getParentCount() > 0) {
                        RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                        CanonicalTreeParser parentTreeIter = new CanonicalTreeParser();
                        parentTreeIter.reset(reader, parent.getTree().getId());
                        oldTreeIter = parentTreeIter;
                    } else {
                        oldTreeIter = new EmptyTreeIterator();
                    }

                    int linesAdded = 0;
                    int linesRemoved = 0;

                    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repository);
                        df.setDetectRenames(true);

                        List<DiffEntry> diffs = df.scan(oldTreeIter, newTreeIter);
                        for (DiffEntry diff : diffs) {
                            FileHeader fh = df.toFileHeader(diff);
                            for (Edit edit : fh.toEditList()) {
                                linesAdded += edit.getLengthB();
                                linesRemoved += edit.getLengthA();
                            }
                        }
                    }

                    int[] currentContributions = authorContributions.getOrDefault(authorId, new int[]{0, 0, 0});
                    currentContributions[0] += linesAdded;
                    currentContributions[1] += linesRemoved;
                    currentContributions[2] += 1; // commit count
                    authorContributions.put(authorId, currentContributions);

                    log.info("Student ID {}: +{} -{} lines for commit {}", authorId, linesAdded, linesRemoved, commitHash);
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing repository at " + localPath, e);
        }
    }

    /**
     * Main method to orchestrate the analysis.
     *
     * @param teamRepositories The list of DTOs containing participation and logs.
     * @return A map of student email to their total lines added and removed [added, removed].
     */
    public Map<Long, int[]> processAllRepositories(List<TeamRepositoryDTO> teamRepositories) {
        authorContributions.clear();

        for (TeamRepositoryDTO repo : teamRepositories) {
            // 1. Map commits to student emails (based on logs)
            Map<String, Long> commitToAuthor = mapCommitToAuthor(repo);

            // 2. Iterate and analyze each repository
            String localPath = repo.localPath();
            try {
                // The results are accumulated into the authorContributions map
                analyzeRepositoryContributions(localPath, commitToAuthor);
            } catch (IOException e) {
                System.err.println("Error processing repository " + repo.participation().repositoryUri() + ": " + e.getMessage());
                // Handle or log error
            }
        }
        return authorContributions;
    }
}

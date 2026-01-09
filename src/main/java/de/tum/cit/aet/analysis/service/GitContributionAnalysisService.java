package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class GitContributionAnalysisService {

    /**
     * Result of mapping commits to authors, including orphan commits.
     */
    private record CommitMappingResult(
            Map<String, Long> commitToAuthor,
            Set<String> orphanCommitHashes,
            Map<String, String> commitToEmail) {
    }

    /**
     * Maps each commit hash to the corresponding author ID based on the VCS logs
     * and team participation data. Also tracks orphan commits.
     *
     * @param repo The TeamRepositoryDTO containing participation and logs.
     * @return CommitMappingResult with mapping data and orphan info.
     */
    private CommitMappingResult mapCommitToAuthor(TeamRepositoryDTO repo) {
        Map<String, Long> commitToStudent = new HashMap<>();
        Map<String, Long> emailToStudent = new HashMap<>();
        Set<String> orphanCommitHashes = new HashSet<>();
        Map<String, String> commitToEmail = new HashMap<>();

        // Map registered student emails to their IDs
        repo.participation().team().students()
                .forEach(student -> emailToStudent.put(student.email().toLowerCase(), student.id()));

        for (VCSLogDTO logEntry : repo.vcsLogs()) {
            String commitHash = logEntry.commitHash();
            String email = logEntry.email();
            commitToEmail.put(commitHash, email);

            // Try to match email (case-insensitive)
            Long studentId = emailToStudent.get(email != null ? email.toLowerCase() : null);

            if (studentId != null) {
                commitToStudent.put(commitHash, studentId);
            } else {
                // This is an orphan commit - email doesn't match any registered student
                orphanCommitHashes.add(commitHash);
                log.debug("Orphan commit detected: {} with email {}", commitHash, email);
            }
        }

        return new CommitMappingResult(commitToStudent, orphanCommitHashes, commitToEmail);
    }

    /**
     * Analyzes the Git repository and returns both contributions and orphan
     * commits.
     *
     * @param repo The TeamRepositoryDTO to analyze.
     * @return RepositoryAnalysisResultDTO with contributions and orphans.
     */
    public RepositoryAnalysisResultDTO analyzeRepositoryWithOrphans(TeamRepositoryDTO repo) {
        CommitMappingResult mapping = mapCommitToAuthor(repo);
        String localPath = repo.localPath();

        try {
            return analyzeRepositoryContributionsWithOrphans(
                    localPath,
                    mapping.commitToAuthor(),
                    mapping.orphanCommitHashes(),
                    mapping.commitToEmail());
        } catch (IOException e) {
            log.error("Error processing repository {}: {}", repo.participation().repositoryUri(), e.getMessage());
            return RepositoryAnalysisResultDTO.empty();
        }
    }

    /**
     * Analyzes the Git repository at the given local path and returns contributions
     * along with orphan commit details.
     */
    private RepositoryAnalysisResultDTO analyzeRepositoryContributionsWithOrphans(
            String localPath,
            Map<String, Long> commitToAuthor,
            Set<String> orphanCommitHashes,
            Map<String, String> commitToEmail) throws IOException {

        log.info("Running git analysis on local path: {}", localPath);
        Map<Long, AuthorContributionDTO> repoContributions = new HashMap<>();
        List<OrphanCommitDTO> orphanCommits = new ArrayList<>();

        File gitDir = new File(localPath, ".git");

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            try (RevWalk revWalk = new RevWalk(repository);
                    DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

                df.setRepository(repository);
                df.setDetectRenames(true);

                // Collect all commit hashes to process
                Set<String> allCommitHashes = new HashSet<>(commitToAuthor.keySet());
                allCommitHashes.addAll(orphanCommitHashes);

                for (String commitHash : allCommitHashes) {
                    ObjectId commitId = repository.resolve(commitHash);
                    if (commitId == null) {
                        log.warn("Unable to resolve commit {}", commitHash);
                        continue;
                    }

                    RevCommit commit = revWalk.parseCommit(commitId);
                    RevCommit oldCommit = (commit.getParentCount() > 0)
                            ? revWalk.parseCommit(commit.getParent(0).getId())
                            : null;

                    int linesAdded = 0;
                    int linesDeleted = 0;

                    List<DiffEntry> diffs = df.scan(oldCommit, commit);
                    for (DiffEntry diff : diffs) {
                        FileHeader fh = df.toFileHeader(diff);
                        for (Edit edit : fh.toEditList()) {
                            linesAdded += edit.getLengthB();
                            linesDeleted += edit.getLengthA();
                        }
                    }

                    if (orphanCommitHashes.contains(commitHash)) {
                        // This is an orphan commit
                        LocalDateTime timestamp = LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(commit.getCommitTime()),
                                ZoneId.systemDefault());

                        orphanCommits.add(new OrphanCommitDTO(
                                commitHash,
                                commitToEmail.getOrDefault(commitHash, commit.getAuthorIdent().getEmailAddress()),
                                commit.getAuthorIdent().getName(),
                                commit.getShortMessage(),
                                timestamp,
                                linesAdded,
                                linesDeleted));

                        log.info("Orphan commit {}: {} (+{} -{} lines)",
                                commitHash.substring(0, 7),
                                commit.getShortMessage(),
                                linesAdded,
                                linesDeleted);
                    } else {
                        // This is a regular student commit
                        Long authorId = commitToAuthor.get(commitHash);
                        if (authorId != null) {
                            AuthorContributionDTO current = repoContributions.getOrDefault(authorId,
                                    new AuthorContributionDTO(0, 0, 0));
                            repoContributions.put(authorId, new AuthorContributionDTO(
                                    current.linesAdded() + linesAdded,
                                    current.linesDeleted() + linesDeleted,
                                    current.commitCount() + 1));

                            log.debug("Student ID {}: +{} -{} lines for commit {}",
                                    authorId, linesAdded, linesDeleted, commitHash);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing repository at " + localPath, e);
        }

        if (!orphanCommits.isEmpty()) {
            log.warn("Found {} orphan commits in repository at {}", orphanCommits.size(), localPath);
        }

        return new RepositoryAnalysisResultDTO(repoContributions, orphanCommits);
    }

    // ========== Legacy methods for backward compatibility ==========

    /**
     * Maps each commit hash to the corresponding author ID (legacy method).
     */
    private Map<String, Long> mapCommitToAuthorLegacy(TeamRepositoryDTO repo) {
        Map<String, Long> commitToStudent = new HashMap<>();
        Map<String, Long> emailToStudent = new HashMap<>();
        repo.participation().team().students().forEach(student -> emailToStudent.put(student.email(), student.id()));
        for (VCSLogDTO logEntry : repo.vcsLogs()) {
            commitToStudent.put(logEntry.commitHash(), emailToStudent.get(logEntry.email()));
        }
        return commitToStudent;
    }

    /**
     * Analyzes the Git repository (legacy - no orphan tracking).
     */
    public Map<Long, AuthorContributionDTO> analyzeRepositoryContributions(String localPath,
            Map<String, Long> commitToAuthor) throws IOException {
        log.info("Running git analysis on local path: {}", localPath);
        Map<Long, AuthorContributionDTO> repoContributions = new HashMap<>();

        File gitDir = new File(localPath, ".git");

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            try (RevWalk revWalk = new RevWalk(repository);
                    DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

                df.setRepository(repository);
                df.setDetectRenames(true);

                for (Map.Entry<String, Long> entry : commitToAuthor.entrySet()) {
                    String commitHash = entry.getKey();
                    Long authorId = entry.getValue();

                    ObjectId commitId = repository.resolve(commitHash);
                    if (commitId == null) {
                        log.warn("Unable to resolve commit {}", commitHash);
                        continue;
                    }

                    RevCommit commit = revWalk.parseCommit(commitId);
                    RevCommit oldCommit = (commit.getParentCount() > 0)
                            ? revWalk.parseCommit(commit.getParent(0).getId())
                            : null;

                    int linesAdded = 0;
                    int linesDeleted = 0;

                    List<DiffEntry> diffs = df.scan(oldCommit, commit);
                    for (DiffEntry diff : diffs) {
                        FileHeader fh = df.toFileHeader(diff);
                        for (Edit edit : fh.toEditList()) {
                            linesAdded += edit.getLengthB();
                            linesDeleted += edit.getLengthA();
                        }
                    }

                    AuthorContributionDTO currentContributions = repoContributions.getOrDefault(authorId,
                            new AuthorContributionDTO(0, 0, 0));
                    repoContributions.put(authorId, new AuthorContributionDTO(
                            currentContributions.linesAdded() + linesAdded,
                            currentContributions.linesDeleted() + linesDeleted,
                            currentContributions.commitCount() + 1));

                    log.info("Student ID {}: +{} -{} lines for commit {}", authorId, linesAdded, linesDeleted,
                            commitHash);
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing repository at " + localPath, e);
        }
        return repoContributions;
    }

    /**
     * Processes a single team repository (legacy).
     */
    public Map<Long, AuthorContributionDTO> analyzeRepository(TeamRepositoryDTO repo) {
        Map<String, Long> commitToAuthor = mapCommitToAuthorLegacy(repo);
        String localPath = repo.localPath();
        try {
            return analyzeRepositoryContributions(localPath, commitToAuthor);
        } catch (IOException e) {
            log.error("Error processing repository {}: {}", repo.participation().repositoryUri(), e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Processes all team repositories (legacy).
     */
    public Map<Long, AuthorContributionDTO> processAllRepositories(List<TeamRepositoryDTO> teamRepositories) {
        Map<Long, AuthorContributionDTO> allContributions = new HashMap<>();

        for (TeamRepositoryDTO repo : teamRepositories) {
            Map<String, Long> commitToAuthor = mapCommitToAuthorLegacy(repo);
            String localPath = repo.localPath();
            try {
                Map<Long, AuthorContributionDTO> repoContributions = analyzeRepositoryContributions(localPath,
                        commitToAuthor);
                repoContributions.forEach((authorId, dto) -> {
                    AuthorContributionDTO existing = allContributions.getOrDefault(authorId,
                            new AuthorContributionDTO(0, 0, 0));
                    allContributions.put(authorId, new AuthorContributionDTO(
                            existing.linesAdded() + dto.linesAdded(),
                            existing.linesDeleted() + dto.linesDeleted(),
                            existing.commitCount() + dto.commitCount()));
                });
            } catch (IOException e) {
                log.error("Error processing repository {}: {}", repo.participation().repositoryUri(), e.getMessage());
            }
        }
        return allContributions;
    }
}

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

        // Map registered student emails to their IDs (VCS emails from Artemis)
        repo.participation().team().students().forEach(student -> {
            if (student.email() != null) {
                emailToStudent.put(student.email().toLowerCase(), student.id());
            }
        });

        for (VCSLogDTO logEntry : repo.vcsLogs()) {
            String commitHash = logEntry.commitHash();
            String email = logEntry.email();
            commitToEmail.put(commitHash, email);

            // Match email directly (both from Artemis)
            Long studentId = null;
            if (email != null) {
                studentId = emailToStudent.get(email.toLowerCase());
            }

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

    // ========== Public utility methods ==========

    /**
     * Builds a mapping from commit SHA to synthetic author ID from VCS logs.
     * This creates synthetic IDs based on unique email addresses, useful when
     * real student IDs are not available or not needed.
     *
     * @param repo The repository containing VCS logs
     * @return Map of commit hashes to synthetic author IDs
     */
    public Map<String, Long> buildCommitToAuthorMap(TeamRepositoryDTO repo) {
        Map<String, Long> mapping = new HashMap<>();
        Map<String, Long> emailToId = new HashMap<>();
        long idCounter = 1;

        if (repo.vcsLogs() == null) {
            return mapping;
        }

        for (var logEntry : repo.vcsLogs()) {
            if (logEntry.commitHash() == null || logEntry.email() == null) {
                continue;
            }

            Long authorId = emailToId.get(logEntry.email());
            if (authorId == null) {
                authorId = idCounter++;
                emailToId.put(logEntry.email(), authorId);
            }
            mapping.put(logEntry.commitHash(), authorId);
        }

        log.debug("Mapped {} commits to {} unique authors", mapping.size(), emailToId.size());
        return mapping;
    }

    // ========== Legacy methods for backward compatibility ==========

    /**
     * Maps each commit hash to the corresponding author ID (legacy method).
     * Uses real student IDs from the database instead of synthetic IDs.
     */
    private Map<String, Long> mapCommitToAuthorLegacy(TeamRepositoryDTO repo) {
        Map<String, Long> commitToStudent = new HashMap<>();
        Map<String, Long> emailToStudent = new HashMap<>();
        
        // Map student emails directly (both from Artemis)
        repo.participation().team().students().forEach(student -> {
            if (student.email() != null) {
                emailToStudent.put(student.email().toLowerCase(), student.id());
            }
        });
        
        for (VCSLogDTO logEntry : repo.vcsLogs()) {
            String email = logEntry.email();
            Long studentId = null;
            if (email != null) {
                studentId = emailToStudent.get(email.toLowerCase());
            }
            if (studentId != null) {
                commitToStudent.put(logEntry.commitHash(), studentId);
            }
        }
        return commitToStudent;
    }

    /**
     * Analyzes the Git repository (legacy - no orphan tracking).
     *
     * @param localPath      The local path to the repository
     * @param commitToAuthor Map of commit hashes to author IDs
     * @return Map of author IDs to contribution stats
     * @throws IOException If the repository cannot be accessed
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
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing repository at " + localPath, e);
        }
        return repoContributions;
    }

    /**
     * Processes a single team repository (legacy).
     *
     * @param repo The repository to analyze
     * @return Map of author IDs to contribution stats
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
     *
     * @param teamRepositories List of repositories to analyze
     * @return Map of author IDs to contribution stats
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

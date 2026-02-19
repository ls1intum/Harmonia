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
     * Result of mapping ALL commits (from full git history walk) to authors.
     *
     * @param commitToAuthor   hash -> studentId for all assigned commits
     * @param orphanCommitHashes hashes that could not be assigned to any student
     * @param commitToVcsEmail hash -> VCS email (only for commits present in VCS logs)
     */
    public record FullCommitMappingResult(
            Map<String, Long> commitToAuthor,
            Set<String> orphanCommitHashes,
            Map<String, String> commitToVcsEmail) {

        public static FullCommitMappingResult empty() {
            return new FullCommitMappingResult(Map.of(), Set.of(), Map.of());
        }
    }

    /**
     * Walks the full git history from HEAD and maps every commit to an author
     * using a 3-tier matching strategy:
     * <ol>
     *   <li><b>VCS-Log anchor</b>: commit hash found in VCS logs -> use Artemis email</li>
     *   <li><b>Learned mapping</b>: for VCS-log commits we know both Artemis email and
     *       git-author email; intermediate commits with a known git email are assigned
     *       via the learned gitEmail -> studentId mapping</li>
     *   <li><b>Direct email match</b>: git-author email matched against Artemis student emails</li>
     * </ol>
     *
     * @param repo the repository DTO (must have a non-null localPath)
     * @return mapping result covering all reachable commits
     */
    public FullCommitMappingResult buildFullCommitMap(TeamRepositoryDTO repo) {
        if (repo.localPath() == null) {
            log.debug("localPath is null, returning empty commit map");
            return FullCommitMappingResult.empty();
        }

        List<VCSLogDTO> vcsLogs = repo.vcsLogs() != null ? repo.vcsLogs() : List.of();
        List<ParticipantDTO> students = repo.participation().team().students();

        return buildFullCommitMap(repo.localPath(), vcsLogs, students);
    }

    /**
     * Lower-level overload that accepts raw parameters instead of a DTO.
     * Useful when the caller has JPA entities and converts them to lists.
     */
    public FullCommitMappingResult buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students) {

        if (localPath == null) {
            return FullCommitMappingResult.empty();
        }

        // --- 1. Build lookup structures ---

        // Artemis email (lowercase) -> studentId
        Map<String, Long> emailToStudentId = new HashMap<>();
        for (ParticipantDTO student : students) {
            if (student.email() != null) {
                emailToStudentId.put(student.email().toLowerCase(), student.id());
            }
        }

        // VCS log hash -> VCS email (from Artemis, trustworthy)
        Map<String, String> vcsHashToEmail = new HashMap<>();
        for (VCSLogDTO logEntry : vcsLogs) {
            if (logEntry.commitHash() != null && logEntry.email() != null) {
                vcsHashToEmail.put(logEntry.commitHash(), logEntry.email());
            }
        }

        // --- 2. Walk git history and build learned mapping ---

        Map<String, Long> commitToAuthor = new HashMap<>();
        Set<String> orphanCommitHashes = new HashSet<>();
        Map<String, String> commitToVcsEmail = new HashMap<>(vcsHashToEmail);

        // Learned mapping: gitEmail (lowercase) -> studentId
        // Built from VCS-log anchor commits where we can correlate git-author email
        // with the Artemis email (and thus the studentId).
        Map<String, Long> learnedGitEmailToStudentId = new HashMap<>();

        File gitDir = new File(localPath, ".git");
        if (!gitDir.exists()) {
            log.warn("Git directory not found at {}", gitDir.getAbsolutePath());
            return FullCommitMappingResult.empty();
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            ObjectId head = repository.resolve("HEAD");
            if (head == null) {
                log.warn("HEAD could not be resolved in {}", localPath);
                return FullCommitMappingResult.empty();
            }

            // Collect all commits reachable from HEAD
            List<RevCommit> allCommits = new ArrayList<>();
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(head));
                for (RevCommit commit : revWalk) {
                    allCommits.add(commit);
                }
            }

            // --- Pass 1: Build learned mapping from VCS-log anchor commits ---
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                String vcsEmail = vcsHashToEmail.get(hash);
                if (vcsEmail == null) {
                    continue;
                }

                // This commit is a VCS-log anchor
                Long studentId = emailToStudentId.get(vcsEmail.toLowerCase());
                if (studentId == null) {
                    continue;
                }

                // Learn the git-author email -> studentId mapping
                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                if (gitEmail != null) {
                    learnedGitEmailToStudentId.put(gitEmail.toLowerCase(), studentId);
                }
            }

            log.debug("Learned {} git-email -> studentId mappings from VCS anchors",
                    learnedGitEmailToStudentId.size());

            // --- Pass 2: Assign every commit using 3-tier matching ---
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                String gitEmailLower = gitEmail != null ? gitEmail.toLowerCase() : null;

                Long studentId = null;

                // Tier 1: VCS-Log anchor
                String vcsEmail = vcsHashToEmail.get(hash);
                if (vcsEmail != null) {
                    studentId = emailToStudentId.get(vcsEmail.toLowerCase());
                }

                // Tier 2: Learned mapping (gitEmail -> studentId)
                if (studentId == null && gitEmailLower != null) {
                    studentId = learnedGitEmailToStudentId.get(gitEmailLower);
                }

                // Tier 3: Direct email match (git email against Artemis emails)
                if (studentId == null && gitEmailLower != null) {
                    studentId = emailToStudentId.get(gitEmailLower);
                }

                if (studentId != null) {
                    commitToAuthor.put(hash, studentId);
                } else {
                    orphanCommitHashes.add(hash);
                    log.debug("Orphan commit: {} by {}", hash.substring(0, Math.min(7, hash.length())),
                            gitEmail);
                }
            }

        } catch (IOException e) {
            log.error("Error walking git history at {}: {}", localPath, e.getMessage());
            return FullCommitMappingResult.empty();
        }

        log.info("Full commit map: {} assigned, {} orphan (from {} total reachable commits)",
                commitToAuthor.size(), orphanCommitHashes.size(),
                commitToAuthor.size() + orphanCommitHashes.size());

        return new FullCommitMappingResult(commitToAuthor, orphanCommitHashes, commitToVcsEmail);
    }

    // ========== Methods that delegate to buildFullCommitMap ==========

    /**
     * Result of mapping commits to authors, including orphan commits.
     */
    private record CommitMappingResult(
            Map<String, Long> commitToAuthor,
            Set<String> orphanCommitHashes,
            Map<String, String> commitToEmail) {
    }

    /**
     * Maps each commit hash to the corresponding author ID based on full git
     * history walk with 3-tier matching. Also tracks orphan commits.
     */
    private CommitMappingResult mapCommitToAuthor(TeamRepositoryDTO repo) {
        FullCommitMappingResult full = buildFullCommitMap(repo);
        return new CommitMappingResult(
                new HashMap<>(full.commitToAuthor()),
                new HashSet<>(full.orphanCommitHashes()),
                new HashMap<>(full.commitToVcsEmail()));
    }

    /**
     * Builds a mapping from commit SHA to synthetic author ID by walking full
     * git history. Synthetic IDs are assigned per unique email.
     *
     * @param repo The repository containing VCS logs
     * @return Map of commit hashes to synthetic author IDs
     */
    public Map<String, Long> buildCommitToAuthorMap(TeamRepositoryDTO repo) {
        FullCommitMappingResult full = buildFullCommitMap(repo);

        // Convert real studentIds to synthetic per-email IDs for backward compat
        Map<Long, Long> studentIdToSynthetic = new HashMap<>();
        Map<String, Long> mapping = new HashMap<>();
        long idCounter = 1;

        for (Map.Entry<String, Long> entry : full.commitToAuthor().entrySet()) {
            Long realId = entry.getValue();
            Long syntheticId = studentIdToSynthetic.get(realId);
            if (syntheticId == null) {
                syntheticId = idCounter++;
                studentIdToSynthetic.put(realId, syntheticId);
            }
            mapping.put(entry.getKey(), syntheticId);
        }

        log.debug("Mapped {} commits to {} unique authors (full history walk)",
                mapping.size(), studentIdToSynthetic.size());
        return mapping;
    }

    /**
     * Maps each commit hash to the corresponding author ID using full git
     * history walk with real student IDs.
     */
    private Map<String, Long> mapCommitToAuthorLegacy(TeamRepositoryDTO repo) {
        FullCommitMappingResult full = buildFullCommitMap(repo);
        return new HashMap<>(full.commitToAuthor());
    }

    // ========== Analysis methods (unchanged logic) ==========

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

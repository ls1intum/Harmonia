package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.dto.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
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
     * Walks the full git history and maps every commit to an author using a 3-tier
     * matching strategy (VCS-Log anchor, Learned mapping, Direct email match).
     * When a template author email is provided, ALL commits from that author are
     * marked as template commits (not just unassigned root commits).
     *
     * @param repo                 the repository DTO (must have a non-null localPath)
     * @param templateAuthorEmails emails of the template authors (lowercase), or null/empty
     * @return mapping result covering all reachable commits
     */
    public FullCommitMappingResultDTO buildFullCommitMap(TeamRepositoryDTO repo,
            Set<String> templateAuthorEmails) {
        if (repo.localPath() == null) {
            return FullCommitMappingResultDTO.empty();
        }

        List<VCSLogDTO> vcsLogs = repo.vcsLogs() != null ? repo.vcsLogs() : List.of();
        List<ParticipantDTO> students = repo.participation().team().students();

        return buildFullCommitMap(repo.localPath(), vcsLogs, students, Map.of(),
                templateAuthorEmails);
    }

    /**
     * Walks the full git history from HEAD and maps every commit to an author
     * using raw parameters (local path, VCS logs, participant list).
     *
     * @param localPath             path to the local git repository
     * @param vcsLogs               VCS log entries from Artemis
     * @param students              participants whose contributions are analysed
     * @param manualMappings        gitEmail (lowercase) -> studentId from ExerciseEmailMappings
     * @param templateAuthorEmails  emails of the template authors (lowercase), or null/empty
     * @return mapping result containing per-student commit data
     */
    public FullCommitMappingResultDTO buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students,
            Map<String, Long> manualMappings,
            Set<String> templateAuthorEmails) {

        if (localPath == null) {
            return FullCommitMappingResultDTO.empty();
        }

        // --- 1. Build lookup structures ---

        // Artemis email (lowercase) -> studentId
        Map<String, Long> emailToStudentId = new HashMap<>();
        for (ParticipantDTO student : students) {
            if (student.email() != null) {
                emailToStudentId.put(student.email().toLowerCase(Locale.ROOT), student.id());
            }
        }
        // Tier 0: Manual mappings override all other email mappings
        emailToStudentId.putAll(manualMappings);

        // studentId -> Artemis email (reverse lookup for display emails)
        Map<Long, String> studentIdToEmail = new HashMap<>();
        for (ParticipantDTO student : students) {
            if (student.email() != null && student.id() != null) {
                studentIdToEmail.put(student.id(), student.email());
            }
        }

        // VCS log hash -> list of VCS emails (multi-valued because both students
        // can push the same commit hash after a merge, creating two VCS log entries)
        Map<String, List<String>> vcsHashToEmails = new HashMap<>();
        for (VCSLogDTO logEntry : vcsLogs) {
            if (logEntry.commitHash() != null && logEntry.email() != null) {
                vcsHashToEmails.computeIfAbsent(logEntry.commitHash(), k -> new ArrayList<>())
                        .add(logEntry.email());
            }
        }

        // --- 2. Walk git history and build learned mapping ---

        Map<String, Long> commitToAuthor = new HashMap<>();
        Map<String, String> orphanCommitEmails = new HashMap<>();
        Map<String, String> commitToVcsEmail = new HashMap<>();
        Set<String> templateHashes = new HashSet<>();

        // Learned mapping: gitEmail (lowercase) -> studentId
        // Built from VCS-log anchor commits where we can correlate git-author email
        // with the Artemis email (and thus the studentId).
        Map<String, Long> learnedGitEmailToStudentId = new HashMap<>();

        File gitDir = new File(localPath, ".git");
        if (!gitDir.exists()) {
            log.warn("Git directory not found at {}", gitDir.getAbsolutePath());
            return FullCommitMappingResultDTO.empty();
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            ObjectId head = repository.resolve("HEAD");
            if (head == null) {
                log.warn("HEAD could not be resolved in {}", localPath);
                return FullCommitMappingResultDTO.empty();
            }

            // Collect all commits reachable from HEAD and build git-email lookup
            List<RevCommit> allCommits = new ArrayList<>();
            Map<String, String> hashToGitEmail = new HashMap<>();
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.sort(RevSort.COMMIT_TIME_DESC);
                revWalk.markStart(revWalk.parseCommit(head));
                for (RevCommit commit : revWalk) {
                    allCommits.add(commit);
                    String ge = commit.getAuthorIdent().getEmailAddress();
                    if (ge != null && !ge.isBlank()) {
                        hashToGitEmail.put(commit.getName(), ge.toLowerCase(Locale.ROOT));
                    }
                }
            }

            // --- Pass 1: Build learned mapping from unambiguous VCS-log anchor commits ---
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                List<String> vcsEmails = vcsHashToEmails.get(hash);
                if (vcsEmails == null || vcsEmails.isEmpty()) {
                    continue;
                }
                if (vcsEmails.size() > 1) {
                    continue; // ambiguous — resolve after Pass 1
                }
                String vcsEmail = vcsEmails.get(0);

                // This commit is a VCS-log anchor
                Long studentId = emailToStudentId.get(vcsEmail.toLowerCase(Locale.ROOT));
                if (studentId == null) {
                    continue;
                }

                // Learn the git-author email -> studentId mapping
                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                if (gitEmail != null && !gitEmail.isBlank()) {
                    String gitEmailLower = gitEmail.toLowerCase(Locale.ROOT);
                    Long existing = learnedGitEmailToStudentId.putIfAbsent(gitEmailLower, studentId);
                    if (existing != null && !existing.equals(studentId)) {
                        log.warn("Git email '{}' maps to multiple students (id {} and {}); keeping first",
                                gitEmailLower, existing, studentId);
                    }
                }
            }

            // --- Resolve multi-valued VCS entries using git-author email ---
            Map<String, String> vcsHashToEmail = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : vcsHashToEmails.entrySet()) {
                List<String> emails = entry.getValue();
                if (emails.size() == 1) {
                    vcsHashToEmail.put(entry.getKey(), emails.get(0));
                } else {
                    String resolved = resolveVcsOverlap(emails, hashToGitEmail.get(entry.getKey()),
                            emailToStudentId, learnedGitEmailToStudentId);
                    vcsHashToEmail.put(entry.getKey(), resolved);
                }
            }

            // --- Pass 2: Assign every commit using 3-tier matching ---
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                String gitEmailLower = (gitEmail != null && !gitEmail.isBlank())
                        ? gitEmail.toLowerCase(Locale.ROOT) : null;

                Long studentId = null;

                // Tier 1: VCS-Log anchor
                String vcsEmail = vcsHashToEmail.get(hash);
                if (vcsEmail != null) {
                    studentId = emailToStudentId.get(vcsEmail.toLowerCase(Locale.ROOT));
                    if (studentId != null) {
                        commitToAuthor.put(hash, studentId);
                        commitToVcsEmail.put(hash, vcsEmail);
                    } else {
                        String orphanEmail = gitEmailLower != null ? gitEmailLower : "unknown";
                        orphanCommitEmails.put(hash, orphanEmail);
                    }
                    continue; // VCS anchor is authoritative — never fall through
                }

                // Tier 2: Learned mapping (gitEmail -> studentId)
                if (gitEmailLower != null) {
                    studentId = learnedGitEmailToStudentId.get(gitEmailLower);
                }

                // Tier 3: Direct email match (git email against Artemis emails)
                if (studentId == null && gitEmailLower != null) {
                    studentId = emailToStudentId.get(gitEmailLower);
                }

                if (studentId != null) {
                    commitToAuthor.put(hash, studentId);
                    String artemisEmail = studentIdToEmail.get(studentId);
                    if (artemisEmail != null) {
                        commitToVcsEmail.put(hash, artemisEmail);
                    }
                } else {
                    String orphanEmail = gitEmailLower != null ? gitEmailLower : "unknown";
                    orphanCommitEmails.put(hash, orphanEmail);
                }
            }

            // --- Pass 3: Detect template commits ---
            // If template author emails are configured, ALL their commits are template.
            // Otherwise, only unassigned root commits are detected as template.
            Set<String> templateEmailSet = templateAuthorEmails != null ? templateAuthorEmails : Set.of();
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                if (commitToAuthor.containsKey(hash)) {
                    continue; // Already assigned to a student
                }

                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                String gitEmailLower = gitEmail != null ? gitEmail.toLowerCase(Locale.ROOT) : "";

                boolean isTemplate = false;
                if (!templateEmailSet.isEmpty()
                        && templateEmailSet.contains(gitEmailLower)) {
                    isTemplate = true; // All commits from template authors
                } else if (commit.getParentCount() == 0) {
                    isTemplate = true; // Fallback: unassigned root commits
                }

                if (isTemplate) {
                    templateHashes.add(hash);
                    orphanCommitEmails.remove(hash);
                }
            }
        } catch (IOException e) {
            log.error("Error walking git history at {}: {}", localPath, e.getMessage());
            return FullCommitMappingResultDTO.empty();
        }

        return new FullCommitMappingResultDTO(commitToAuthor, orphanCommitEmails, commitToVcsEmail,
                templateHashes);
    }

    /**
     * Resolves an ambiguous VCS-log overlap where multiple emails are recorded
     * for the same commit hash. Uses the git-author email to pick the correct
     * VCS email by matching through the learned and direct email mappings.
     *
     * @param vcsEmails                  the candidate VCS emails (size > 1)
     * @param gitEmailLower              the git-author email (lowercase), may be null
     * @param emailToStudentId           Artemis email -> studentId
     * @param learnedGitEmailToStudentId learned git email -> studentId
     * @return the resolved VCS email, or the first candidate as fallback
     */
    private static String resolveVcsOverlap(List<String> vcsEmails, String gitEmailLower,
            Map<String, Long> emailToStudentId, Map<String, Long> learnedGitEmailToStudentId) {
        if (gitEmailLower != null) {
            // Look up studentId for the git-author email
            Long gitStudentId = learnedGitEmailToStudentId.get(gitEmailLower);
            if (gitStudentId == null) {
                gitStudentId = emailToStudentId.get(gitEmailLower);
            }
            if (gitStudentId != null) {
                for (String candidate : vcsEmails) {
                    Long candidateId = emailToStudentId.get(candidate.toLowerCase(Locale.ROOT));
                    if (gitStudentId.equals(candidateId)) {
                        return candidate;
                    }
                }
            }
        }
        // Fallback: first VCS email
        return vcsEmails.get(0);
    }

    /**
     * Finds the author emails of all root commits (parentCount == 0) in the given
     * repository. Used for cross-repo template author detection.
     *
     * @param localPath path to the local git repository
     * @return set of lowercase author emails from root commits
     */
    public Set<String> findRootCommitEmails(String localPath) {
        Set<String> rootEmails = new HashSet<>();
        if (localPath == null) {
            return rootEmails;
        }

        File gitDir = new File(localPath, ".git");
        if (!gitDir.exists()) {
            log.warn("Git directory not found at {} for root commit detection", gitDir.getAbsolutePath());
            return rootEmails;
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build()) {

            ObjectId head = repository.resolve("HEAD");
            if (head == null) {
                return rootEmails;
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(head));
                for (RevCommit commit : revWalk) {
                    if (commit.getParentCount() == 0) {
                        String email = commit.getAuthorIdent().getEmailAddress();
                        if (email != null && !email.isBlank()) {
                            rootEmails.add(email.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error finding root commit emails at {}: {}", localPath, e.getMessage());
        }

        return rootEmails;
    }

    // ========== Commit mapping & analysis ==========

    /**
     * Maps commits to authors and separates orphan commits (excluding templates).
     * Template commits are already excluded by {@link #buildFullCommitMap}.
     *
     * @param repo                 the repository DTO
     * @param templateAuthorEmails emails of the template authors (lowercase), or null/empty
     * @return mapping result with assigned and orphan commits
     */
    public CommitMappingResultDTO mapCommitToAuthor(TeamRepositoryDTO repo, Set<String> templateAuthorEmails) {
        FullCommitMappingResultDTO full = buildFullCommitMap(repo, templateAuthorEmails);
        return new CommitMappingResultDTO(
                full.commitToAuthor(),
                full.orphanCommitEmails(),
                full.commitToVcsEmail());
    }

    /**
     * Analyzes the Git repository and returns contributions and orphan commits,
     * excluding any commits authored by the template author.
     *
     * @param repo                 The TeamRepositoryDTO to analyze.
     * @param templateAuthorEmails Emails of the template authors (lowercase), or null/empty.
     * @return RepositoryAnalysisResultDTO with contributions and orphans.
     */
    public RepositoryAnalysisResultDTO analyzeRepositoryWithOrphans(
            TeamRepositoryDTO repo, Set<String> templateAuthorEmails) {
        CommitMappingResultDTO mapping = mapCommitToAuthor(repo, templateAuthorEmails);
        String localPath = repo.localPath();

        try {
            return analyzeRepositoryContributionsWithOrphans(
                    localPath,
                    mapping.commitToAuthor(),
                    mapping.orphanCommitEmails().keySet(),
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
     * Analyzes a single repository and returns per-author contribution stats.
     * Delegates to {@link #analyzeRepositoryWithOrphans}, discarding orphan data.
     *
     * @param repo the repository to analyze
     * @return map of author IDs to contribution stats
     */
    public Map<Long, AuthorContributionDTO> analyzeRepository(TeamRepositoryDTO repo) {
        return analyzeRepositoryWithOrphans(repo, null).contributions();
    }

    /**
     * Analyzes multiple repositories and returns merged per-author contribution stats.
     * Delegates to {@link #analyzeRepositoryWithOrphans} for each repo, discarding orphan data.
     *
     * @param teamRepositories list of repositories to analyze
     * @return map of author IDs to aggregated contribution stats
     */
    public Map<Long, AuthorContributionDTO> processAllRepositories(List<TeamRepositoryDTO> teamRepositories) {
        Map<Long, AuthorContributionDTO> all = new HashMap<>();
        for (TeamRepositoryDTO repo : teamRepositories) {
            analyzeRepository(repo).forEach((id, dto) -> {
                AuthorContributionDTO existing = all.getOrDefault(id, new AuthorContributionDTO(0, 0, 0));
                all.put(id, new AuthorContributionDTO(
                        existing.linesAdded() + dto.linesAdded(),
                        existing.linesDeleted() + dto.linesDeleted(),
                        existing.commitCount() + dto.commitCount()));
            });
        }
        return all;
    }
}

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
     * Result of mapping ALL commits (from full git history walk) to authors.
     *
     * @param commitToAuthor       hash -> studentId for all assigned commits
     * @param orphanCommitEmails   hash -> git author email for commits that could not be assigned
     * @param commitToVcsEmail     hash -> display email for all assigned commits
     *                             (VCS email for Tier 1 anchors, Artemis email for Tier 2/3)
     * @param templateCommitHashes hashes of root commits (parentCount == 0) whose author
     *                             could not be resolved to any student — typically the
     *                             course-template initial commit
     */
    public record FullCommitMappingResult(
            Map<String, Long> commitToAuthor,
            Map<String, String> orphanCommitEmails,
            Map<String, String> commitToVcsEmail,
            Set<String> templateCommitHashes) {

        public static FullCommitMappingResult empty() {
            return new FullCommitMappingResult(Map.of(), Map.of(), Map.of(), Set.of());
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
        return buildFullCommitMap(repo, null);
    }

    /**
     * Walks the full git history with template author email for enhanced detection.
     * When a template author email is provided, ALL commits from that author are
     * marked as template commits (not just unassigned root commits).
     *
     * @param repo                the repository DTO (must have a non-null localPath)
     * @param templateAuthorEmail email of the template author (lowercase), or null
     * @return mapping result covering all reachable commits
     */
    public FullCommitMappingResult buildFullCommitMap(TeamRepositoryDTO repo,
            String templateAuthorEmail) {
        if (repo.localPath() == null) {
            log.debug("localPath is null, returning empty commit map");
            return FullCommitMappingResult.empty();
        }

        List<VCSLogDTO> vcsLogs = repo.vcsLogs() != null ? repo.vcsLogs() : List.of();
        List<ParticipantDTO> students = repo.participation().team().students();

        return buildFullCommitMap(repo.localPath(), vcsLogs, students, Map.of(),
                templateAuthorEmail);
    }

    /**
     * Lower-level overload that accepts raw parameters instead of a DTO.
     *
     * @param localPath path to the local git repository
     * @param vcsLogs   VCS log entries from Artemis
     * @param students  participants whose contributions are analysed
     * @return mapping result containing per-student commit data
     */
    public FullCommitMappingResult buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students) {
        return buildFullCommitMap(localPath, vcsLogs, students, Map.of());
    }

    /**
     * Lower-level overload that also accepts manual email-to-student mappings.
     * Manual mappings act as "Tier 0" — they override all other matching tiers.
     *
     * @param localPath       path to the local git repository
     * @param vcsLogs         VCS log entries from Artemis
     * @param students        participants whose contributions are analysed
     * @param manualMappings  gitEmail (lowercase) -> studentId from ExerciseEmailMappings
     * @return mapping result containing per-student commit data
     */
    public FullCommitMappingResult buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students,
            Map<String, Long> manualMappings) {
        return buildFullCommitMap(localPath, vcsLogs, students, manualMappings, null);
    }

    /**
     * Full overload that also accepts a template author email.
     * When provided, ALL commits from this author are marked as template
     * (not just unassigned root commits).
     *
     * @param localPath            path to the local git repository
     * @param vcsLogs              VCS log entries from Artemis
     * @param students             participants whose contributions are analysed
     * @param manualMappings       gitEmail (lowercase) -> studentId from ExerciseEmailMappings
     * @param templateAuthorEmail  email of the template author (lowercase), or null
     * @return mapping result containing per-student commit data
     */
    public FullCommitMappingResult buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students,
            Map<String, Long> manualMappings,
            String templateAuthorEmail) {

        if (localPath == null) {
            return FullCommitMappingResult.empty();
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

            log.debug("Learned {} git-email -> studentId mappings from VCS anchors",
                    learnedGitEmailToStudentId.size());

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
                        log.debug("Orphan commit (VCS anchor unresolved): {} by {}",
                                hash.substring(0, Math.min(7, hash.length())), gitEmail);
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
                    log.debug("Orphan commit: {} by {}", hash.substring(0, Math.min(7, hash.length())),
                            gitEmail);
                }
            }

            // --- Pass 3: Detect template commits ---
            // If a template author email is configured, ALL their commits are template.
            // Otherwise, only unassigned root commits are detected as template.
            for (RevCommit commit : allCommits) {
                String hash = commit.getName();
                if (commitToAuthor.containsKey(hash)) {
                    continue; // Already assigned to a student
                }

                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                String gitEmailLower = gitEmail != null ? gitEmail.toLowerCase(Locale.ROOT) : "";

                boolean isTemplate = false;
                if (templateAuthorEmail != null
                        && templateAuthorEmail.equalsIgnoreCase(gitEmailLower)) {
                    isTemplate = true; // All commits from template author
                } else if (commit.getParentCount() == 0) {
                    isTemplate = true; // Fallback: unassigned root commits
                }

                if (isTemplate) {
                    templateHashes.add(hash);
                    orphanCommitEmails.remove(hash);
                }
            }

            if (!templateHashes.isEmpty()) {
                log.info("Detected {} template commit(s), excluded from orphans",
                        templateHashes.size());
            }

        } catch (IOException e) {
            log.error("Error walking git history at {}: {}", localPath, e.getMessage());
            return FullCommitMappingResult.empty();
        }

        log.info("Full commit map: {} assigned, {} orphan, {} template (from {} total reachable commits)",
                commitToAuthor.size(), orphanCommitEmails.size(), templateHashes.size(),
                commitToAuthor.size() + orphanCommitEmails.size() + templateHashes.size());

        return new FullCommitMappingResult(commitToAuthor, orphanCommitEmails, commitToVcsEmail,
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
                new HashSet<>(full.orphanCommitEmails().keySet()),
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

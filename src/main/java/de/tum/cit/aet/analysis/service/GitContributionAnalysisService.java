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

    /** Bundles the four lookup maps used during commit-to-author mapping. */
    private record StudentLookupContext(
            Map<String, Long> emailToStudentId,
            Map<Long, String> studentIdToEmail,
            Map<String, List<String>> vcsHashToEmails,
            Map<String, Long> learnedGitEmailToStudentId
    ) {
    }

    /** All commits reachable from HEAD together with their git-author emails. */
    private record CommitCollectionResult(
            List<RevCommit> allCommits,
            Map<String, String> hashToGitEmail
    ) {
    }

    /** Per-commit author assignment produced by the 3-tier matching pass. */
    private record CommitAssignmentResult(
            Map<String, Long> commitToAuthor,
            Map<String, String> orphanCommitEmails,
            Map<String, String> commitToVcsEmail
    ) {
    }

    /**
     * Walks the full git history and maps every commit to an author using a 3-tier
     * matching strategy (VCS-Log anchor, Learned mapping, Direct email match).
     * When a template author email is provided, ALL commits from that author are
     * marked as template commits (not just unassigned root commits).
     *
     * @param repo                the repository DTO (must have a non-null localPath)
     * @param templateAuthorEmail email of the template author (lowercase), or null
     * @return mapping result covering all reachable commits
     */
    public FullCommitMappingResultDTO buildFullCommitMap(TeamRepositoryDTO repo,
            String templateAuthorEmail) {
        if (repo.localPath() == null) {
            return FullCommitMappingResultDTO.empty();
        }

        List<VCSLogDTO> vcsLogs = repo.vcsLogs() != null ? repo.vcsLogs() : List.of();
        List<ParticipantDTO> students = repo.participation().team().students();

        return buildFullCommitMap(repo.localPath(), vcsLogs, students, Map.of(),
                templateAuthorEmail);
    }

    /**
     * Walks the full git history from HEAD and maps every commit to an author
     * using raw parameters (local path, VCS logs, participant list).
     *
     * @param localPath            path to the local git repository
     * @param vcsLogs              VCS log entries from Artemis
     * @param students             participants whose contributions are analysed
     * @param manualMappings       gitEmail (lowercase) -> studentId from ExerciseEmailMappings
     * @param templateAuthorEmail  email of the template author (lowercase), or null
     * @return mapping result containing per-student commit data
     */
    public FullCommitMappingResultDTO buildFullCommitMap(
            String localPath,
            List<VCSLogDTO> vcsLogs,
            List<ParticipantDTO> students,
            Map<String, Long> manualMappings,
            String templateAuthorEmail) {

        if (localPath == null) {
            return FullCommitMappingResultDTO.empty();
        }

        StudentLookupContext ctx = buildLookupContext(students, manualMappings, vcsLogs);

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

            CommitCollectionResult collected = collectCommitsFromHead(repository, head);

            buildLearnedMappingsFromAnchors(collected.allCommits(), ctx);

            Map<String, String> vcsHashToEmail = resolveVcsEntries(ctx, collected.hashToGitEmail());

            CommitAssignmentResult assignment = assignCommitsToAuthors(
                    collected.allCommits(), vcsHashToEmail, ctx);

            Set<String> templateHashes = detectTemplateCommits(
                    collected.allCommits(), assignment.commitToAuthor(), templateAuthorEmail);
            templateHashes.forEach(assignment.orphanCommitEmails()::remove);

            if (!templateHashes.isEmpty()) {
                log.info("Detected {} template commit(s), excluded from orphans",
                        templateHashes.size());
            }

            log.info("Full commit map: {} assigned, {} orphan, {} template (from {} total reachable commits)",
                    assignment.commitToAuthor().size(), assignment.orphanCommitEmails().size(),
                    templateHashes.size(),
                    assignment.commitToAuthor().size() + assignment.orphanCommitEmails().size()
                            + templateHashes.size());

            return new FullCommitMappingResultDTO(
                    assignment.commitToAuthor(), assignment.orphanCommitEmails(),
                    assignment.commitToVcsEmail(), templateHashes);

        } catch (IOException e) {
            log.error("Error walking git history at {}: {}", localPath, e.getMessage());
            return FullCommitMappingResultDTO.empty();
        }
    }

    private static StudentLookupContext buildLookupContext(
            List<ParticipantDTO> students, Map<String, Long> manualMappings, List<VCSLogDTO> vcsLogs) {

        Map<String, Long> emailToStudentId = new HashMap<>();
        for (ParticipantDTO student : students) {
            if (student.email() != null) {
                emailToStudentId.put(student.email().toLowerCase(Locale.ROOT), student.id());
            }
        }
        emailToStudentId.putAll(manualMappings);

        Map<Long, String> studentIdToEmail = new HashMap<>();
        for (ParticipantDTO student : students) {
            if (student.email() != null && student.id() != null) {
                studentIdToEmail.put(student.id(), student.email());
            }
        }

        Map<String, List<String>> vcsHashToEmails = new HashMap<>();
        for (VCSLogDTO logEntry : vcsLogs) {
            if (logEntry.commitHash() != null && logEntry.email() != null) {
                vcsHashToEmails.computeIfAbsent(logEntry.commitHash(), k -> new ArrayList<>())
                        .add(logEntry.email());
            }
        }

        return new StudentLookupContext(emailToStudentId, studentIdToEmail, vcsHashToEmails, new HashMap<>());
    }

    /** Collects all commits reachable from HEAD with their lowercase git-author emails. */
    private static CommitCollectionResult collectCommitsFromHead(
            Repository repository, ObjectId head) throws IOException {
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
        return new CommitCollectionResult(allCommits, hashToGitEmail);
    }

    /**
     * Pass 1: Learns git-author-email to studentId mappings from unambiguous
     * VCS-log anchor commits (single VCS email per commit hash).
     */
    private void buildLearnedMappingsFromAnchors(
            List<RevCommit> allCommits, StudentLookupContext ctx) {
        for (RevCommit commit : allCommits) {
            String hash = commit.getName();
            List<String> vcsEmails = ctx.vcsHashToEmails().get(hash);
            if (vcsEmails == null || vcsEmails.isEmpty() || vcsEmails.size() > 1) {
                continue;
            }
            String vcsEmail = vcsEmails.get(0);

            Long studentId = ctx.emailToStudentId().get(vcsEmail.toLowerCase(Locale.ROOT));
            if (studentId == null) {
                continue;
            }

            String gitEmail = commit.getAuthorIdent().getEmailAddress();
            if (gitEmail != null && !gitEmail.isBlank()) {
                String gitEmailLower = gitEmail.toLowerCase(Locale.ROOT);
                Long existing = ctx.learnedGitEmailToStudentId().putIfAbsent(gitEmailLower, studentId);
                if (existing != null && !existing.equals(studentId)) {
                    log.warn("Git email '{}' maps to multiple students (id {} and {}); keeping first",
                            gitEmailLower, existing, studentId);
                }
            }
        }
    }

    /**
     * Resolves multi-valued VCS entries to a single email per commit hash,
     * using learned and direct email mappings to disambiguate.
     */
    private static Map<String, String> resolveVcsEntries(
            StudentLookupContext ctx, Map<String, String> hashToGitEmail) {
        Map<String, String> vcsHashToEmail = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : ctx.vcsHashToEmails().entrySet()) {
            List<String> emails = entry.getValue();
            if (emails.size() == 1) {
                vcsHashToEmail.put(entry.getKey(), emails.get(0));
            } else {
                String resolved = resolveVcsOverlap(emails, hashToGitEmail.get(entry.getKey()),
                        ctx.emailToStudentId(), ctx.learnedGitEmailToStudentId());
                vcsHashToEmail.put(entry.getKey(), resolved);
            }
        }
        return vcsHashToEmail;
    }

    /**
     * Pass 2: Assigns every commit to an author using 3-tier matching
     * (VCS-Log anchor, Learned mapping, Direct email match).
     */
    private static CommitAssignmentResult assignCommitsToAuthors(
            List<RevCommit> allCommits, Map<String, String> vcsHashToEmail,
            StudentLookupContext ctx) {
        Map<String, Long> commitToAuthor = new HashMap<>();
        Map<String, String> orphanCommitEmails = new HashMap<>();
        Map<String, String> commitToVcsEmail = new HashMap<>();

        for (RevCommit commit : allCommits) {
            String hash = commit.getName();
            String gitEmail = commit.getAuthorIdent().getEmailAddress();
            String gitEmailLower = (gitEmail != null && !gitEmail.isBlank())
                    ? gitEmail.toLowerCase(Locale.ROOT) : null;

            // Tier 1: VCS-Log anchor
            String vcsEmail = vcsHashToEmail.get(hash);
            if (vcsEmail != null) {
                Long studentId = ctx.emailToStudentId().get(vcsEmail.toLowerCase(Locale.ROOT));
                if (studentId != null) {
                    commitToAuthor.put(hash, studentId);
                    commitToVcsEmail.put(hash, vcsEmail);
                } else {
                    orphanCommitEmails.put(hash, gitEmailLower != null ? gitEmailLower : "unknown");
                }
                continue; // VCS anchor is authoritative — never fall through
            }

            // Tier 2: Learned mapping (gitEmail -> studentId)
            Long studentId = null;
            if (gitEmailLower != null) {
                studentId = ctx.learnedGitEmailToStudentId().get(gitEmailLower);
            }

            // Tier 3: Direct email match (git email against Artemis emails)
            if (studentId == null && gitEmailLower != null) {
                studentId = ctx.emailToStudentId().get(gitEmailLower);
            }

            if (studentId != null) {
                commitToAuthor.put(hash, studentId);
                String artemisEmail = ctx.studentIdToEmail().get(studentId);
                if (artemisEmail != null) {
                    commitToVcsEmail.put(hash, artemisEmail);
                }
            } else {
                orphanCommitEmails.put(hash, gitEmailLower != null ? gitEmailLower : "unknown");
            }
        }

        return new CommitAssignmentResult(commitToAuthor, orphanCommitEmails, commitToVcsEmail);
    }

    /**
     * Pass 3: Detects template commits among unassigned commits.
     * If a template author email is configured, ALL their commits are template.
     * Otherwise, only unassigned root commits are detected.
     */
    private static Set<String> detectTemplateCommits(
            List<RevCommit> allCommits, Map<String, Long> commitToAuthor,
            String templateAuthorEmail) {
        Set<String> templateHashes = new HashSet<>();
        for (RevCommit commit : allCommits) {
            String hash = commit.getName();
            if (commitToAuthor.containsKey(hash)) {
                continue;
            }

            String gitEmail = commit.getAuthorIdent().getEmailAddress();
            String gitEmailLower = gitEmail != null ? gitEmail.toLowerCase(Locale.ROOT) : "";

            boolean isTemplate = (templateAuthorEmail != null
                    && templateAuthorEmail.equalsIgnoreCase(gitEmailLower))
                    || commit.getParentCount() == 0;

            if (isTemplate) {
                templateHashes.add(hash);
            }
        }
        return templateHashes;
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
     * @param repo                the repository DTO
     * @param templateAuthorEmail email of the template author (lowercase), or null
     * @return mapping result with assigned and orphan commits
     */
    public CommitMappingResultDTO mapCommitToAuthor(TeamRepositoryDTO repo, String templateAuthorEmail) {
        FullCommitMappingResultDTO full = buildFullCommitMap(repo, templateAuthorEmail);
        return new CommitMappingResultDTO(
                full.commitToAuthor(),
                full.orphanCommitEmails(),
                full.commitToVcsEmail());
    }

    /**
     * Analyzes the Git repository and returns contributions and orphan commits,
     * excluding any commits authored by the template author.
     *
     * @param repo                The TeamRepositoryDTO to analyze.
     * @param templateAuthorEmail Email of the template author (lowercase), or null.
     * @return RepositoryAnalysisResultDTO with contributions and orphans.
     */
    public RepositoryAnalysisResultDTO analyzeRepositoryWithOrphans(
            TeamRepositoryDTO repo, String templateAuthorEmail) {
        CommitMappingResultDTO mapping = mapCommitToAuthor(repo, templateAuthorEmail);
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

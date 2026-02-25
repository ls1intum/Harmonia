package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chunks git commits for LLM-based effort analysis.
 * <ul>
 *   <li>Splits large commits ({@code >500 LoC}) into smaller chunks</li>
 *   <li>Bundles small commits ({@code ≤30 LoC}) from the same author within 60 minutes</li>
 *   <li>Extracts diff content and detects renames, format-only and mass-reformat patterns</li>
 * </ul>
 */
@Service
@Slf4j
public class CommitChunkerService {

    private static final int MAX_LINES_PER_CHUNK = 500;
    private static final int SMALL_COMMIT_THRESHOLD = 30;
    private static final int BUNDLE_TIME_WINDOW_MINUTES = 60;

    /** Skip patterns for generated/dependency/binary files. */
    private static final String[] SKIP_PATTERNS = {
            "node_modules/", "vendor/", "target/", "build/", ".gradle/",
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            ".min.js", ".min.css", ".map",
            ".class", ".jar", ".war",
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".woff2", ".ttf", ".eot"
    };

    // ── Records ──────────────────────────────────────────────────────────

    /**
     * Raw commit data before chunking.
     */
    public record RawCommitData(
            String sha,
            Long authorId,
            String authorEmail,
            String message,
            LocalDateTime timestamp,
            List<FileChange> fileChanges,
            boolean renameDetected,
            boolean formatOnly,
            boolean massReformatFlag) {

        public int totalLinesChanged() {
            return fileChanges.stream().mapToInt(f -> f.linesAdded + f.linesDeleted).sum();
        }

        /** Convenience constructor without detection flags. */
        public RawCommitData(String sha, Long authorId, String authorEmail, String message,
                             LocalDateTime timestamp, List<FileChange> fileChanges) {
            this(sha, authorId, authorEmail, message, timestamp, fileChanges, false, false, false);
        }
    }

    /**
     * Changes to a single file within a commit.
     */
    public record FileChange(
            String filePath,
            String diffContent,
            int linesAdded,
            int linesDeleted,
            boolean isRename,
            int whitespaceOnlyLines) {

        public int totalLines() {
            return linesAdded + linesDeleted;
        }

        /** Convenience constructor without detection flags. */
        public FileChange(String filePath, String diffContent, int linesAdded, int linesDeleted) {
            this(filePath, diffContent, linesAdded, linesDeleted, false, 0);
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Processes a repository and returns chunked commits ready for LLM analysis.
     *
     * @param localPath      path to the cloned repository
     * @param commitToAuthor map of commit SHA → author ID
     * @return chunked commits
     */
    public List<CommitChunkDTO> processRepository(String localPath, Map<String, Long> commitToAuthor) {
        return processRepository(localPath, commitToAuthor, Map.of());
    }

    /**
     * Processes a repository and returns chunked commits ready for LLM analysis.
     * Uses VCS emails from Artemis instead of Git emails when available.
     *
     * @param localPath        path to the cloned repository
     * @param commitToAuthor   map of commit SHA → author ID
     * @param commitToVcsEmail map of commit SHA → VCS email (overrides Git email)
     * @return chunked commits
     */
    public List<CommitChunkDTO> processRepository(String localPath, Map<String, Long> commitToAuthor,
            Map<String, String> commitToVcsEmail) {
        try {
            // 1) Extract raw commit data from git history
            List<RawCommitData> rawCommits = extractCommitData(localPath, commitToAuthor, commitToVcsEmail);

            // 2) Sort by timestamp and bundle small commits
            rawCommits.sort(Comparator.comparing(RawCommitData::timestamp));
            List<RawCommitData> processedCommits = bundleSmallCommits(rawCommits);

            // 3) Chunk large commits into ≤500 LoC pieces
            List<CommitChunkDTO> chunks = new ArrayList<>();
            for (RawCommitData commit : processedCommits) {
                chunks.addAll(chunkCommit(commit));
            }

            return chunks;

        } catch (IOException e) {
            log.error("Failed to process repository {}: {}", localPath, e.getMessage(), e);
            return List.of();
        }
    }

    // ── Git extraction ───────────────────────────────────────────────────

    /**
     * Extracts raw commit data from a Git repository, including rename,
     * format-only and mass-reformat detection flags.
     */
    private List<RawCommitData> extractCommitData(String localPath, Map<String, Long> commitToAuthor,
            Map<String, String> commitToVcsEmail) throws IOException {
        List<RawCommitData> commits = new ArrayList<>();
        File gitDir = new File(localPath, ".git");

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir).readEnvironment().build();
                RevWalk revWalk = new RevWalk(repository);
                DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream());
                DiffFormatter dfWhitespace = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            df.setRepository(repository);
            df.setDetectRenames(true);

            dfWhitespace.setRepository(repository);
            dfWhitespace.setDetectRenames(true);
            dfWhitespace.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);

            for (Map.Entry<String, Long> entry : commitToAuthor.entrySet()) {
                String commitHash = entry.getKey();
                Long authorId = entry.getValue();

                ObjectId commitId = repository.resolve(commitHash);
                if (commitId == null) {
                    log.warn("Unable to resolve commit {}", commitHash);
                    continue;
                }

                RevCommit commit = revWalk.parseCommit(commitId);
                RevCommit parent = commit.getParentCount() > 0
                        ? revWalk.parseCommit(commit.getParent(0).getId())
                        : null;

                // 1) Extract file-level changes with rename and whitespace detection
                List<FileChange> fileChanges = extractFileChanges(repository, df, dfWhitespace, parent, commit);

                LocalDateTime timestamp = LocalDateTime.ofEpochSecond(
                        commit.getCommitTime(), 0, ZoneOffset.UTC);

                // 2) Resolve author email (VCS email from Artemis takes precedence)
                String gitEmail = commit.getAuthorIdent().getEmailAddress();
                String authorEmail = commitToVcsEmail.getOrDefault(commitHash, gitEmail);

                // 3) Detect commit-level flags
                boolean renameDetected = fileChanges.stream().anyMatch(FileChange::isRename);
                boolean formatOnly = isFormatOnlyCommit(fileChanges);
                boolean massReformat = isMassReformatCommit(fileChanges);

                commits.add(new RawCommitData(
                        commitHash, authorId, authorEmail, commit.getShortMessage(),
                        timestamp, fileChanges, renameDetected, formatOnly, massReformat));
            }
        }

        return commits;
    }

    /**
     * Extracts file-level changes for a single commit with rename and whitespace detection.
     */
    private List<FileChange> extractFileChanges(Repository repository, DiffFormatter df,
            DiffFormatter dfWhitespace, RevCommit parent, RevCommit commit) throws IOException {
        List<DiffEntry> diffs = df.scan(parent, commit);

        // 1) Build whitespace-ignoring line counts for format-only detection
        Map<String, Integer> wsIgnoredLines = buildWhitespaceIgnoredMap(dfWhitespace, parent, commit);

        // 2) Process each diff entry
        List<FileChange> changes = new ArrayList<>();
        for (DiffEntry diff : diffs) {
            if (shouldSkipFile(diff.getNewPath())) {
                continue;
            }

            ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
            try (DiffFormatter fileDf = new DiffFormatter(diffOutput)) {
                fileDf.setRepository(repository);
                fileDf.format(diff);
            }

            FileHeader fh = df.toFileHeader(diff);
            int linesAdded = 0;
            int linesDeleted = 0;
            for (Edit edit : fh.toEditList()) {
                linesAdded += edit.getLengthB();
                linesDeleted += edit.getLengthA();
            }

            String filePath = diff.getNewPath().equals("/dev/null") ? diff.getOldPath() : diff.getNewPath();
            boolean isRename = diff.getChangeType() == DiffEntry.ChangeType.RENAME
                    || diff.getChangeType() == DiffEntry.ChangeType.COPY;

            int totalLines = linesAdded + linesDeleted;
            int wsIgnored = wsIgnoredLines.getOrDefault(filePath, totalLines);
            int whitespaceOnlyLines = Math.max(0, totalLines - wsIgnored);

            changes.add(new FileChange(filePath, diffOutput.toString(),
                    linesAdded, linesDeleted, isRename, whitespaceOnlyLines));
        }

        return changes;
    }

    /**
     * Builds a map of file path → whitespace-ignoring line counts.
     */
    private Map<String, Integer> buildWhitespaceIgnoredMap(DiffFormatter dfWhitespace,
            RevCommit parent, RevCommit commit) throws IOException {
        Map<String, Integer> wsIgnoredLines = new HashMap<>();
        for (DiffEntry wsDiff : dfWhitespace.scan(parent, commit)) {
            try {
                FileHeader wsFh = dfWhitespace.toFileHeader(wsDiff);
                int wsLines = 0;
                for (Edit edit : wsFh.toEditList()) {
                    wsLines += edit.getLengthA() + edit.getLengthB();
                }
                String path = wsDiff.getNewPath().equals("/dev/null") ? wsDiff.getOldPath() : wsDiff.getNewPath();
                wsIgnoredLines.put(path, wsLines);
            } catch (Exception ignored) {
                // Whitespace comparison is best-effort
            }
        }
        return wsIgnoredLines;
    }

    // ── Detection helpers ────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code >80%} of all changes are whitespace-only.
     */
    private boolean isFormatOnlyCommit(List<FileChange> changes) {
        if (changes.isEmpty()) {
            return false;
        }
        int totalLines = changes.stream().mapToInt(FileChange::totalLines).sum();
        if (totalLines == 0) {
            return false;
        }
        int whitespaceLines = changes.stream().mapToInt(FileChange::whitespaceOnlyLines).sum();
        return (double) whitespaceLines / totalLines > 0.8;
    }

    /**
     * Returns {@code true} if {@code ≥10} files changed with average {@code <5} lines per file.
     */
    private boolean isMassReformatCommit(List<FileChange> changes) {
        if (changes.size() < 10) {
            return false;
        }
        int totalLines = changes.stream().mapToInt(FileChange::totalLines).sum();
        return (double) totalLines / changes.size() < 5.0;
    }

    private boolean shouldSkipFile(String filePath) {
        if (filePath == null || filePath.equals("/dev/null")) {
            return false;
        }
        for (String pattern : SKIP_PATTERNS) {
            if (filePath.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    // ── Bundling ─────────────────────────────────────────────────────────

    /**
     * Bundles small commits ({@code ≤30 LoC}) from the same author within 60 minutes.
     */
    private List<RawCommitData> bundleSmallCommits(List<RawCommitData> commits) {
        if (commits.isEmpty()) {
            return commits;
        }

        List<RawCommitData> result = new ArrayList<>();
        List<RawCommitData> currentBundle = new ArrayList<>();

        for (RawCommitData commit : commits) {
            if (commit.totalLinesChanged() > SMALL_COMMIT_THRESHOLD) {
                // 1) Large commit — flush any pending bundle, then add as-is
                if (!currentBundle.isEmpty()) {
                    result.add(mergeBundle(currentBundle));
                    currentBundle.clear();
                }
                result.add(commit);
            } else {
                // 2) Small commit — try to add to current bundle
                if (currentBundle.isEmpty()) {
                    currentBundle.add(commit);
                } else {
                    RawCommitData lastInBundle = currentBundle.get(currentBundle.size() - 1);
                    boolean sameAuthor = Objects.equals(commit.authorId(), lastInBundle.authorId());
                    long minutesDiff = Duration.between(lastInBundle.timestamp(), commit.timestamp()).toMinutes();

                    if (sameAuthor && minutesDiff <= BUNDLE_TIME_WINDOW_MINUTES) {
                        currentBundle.add(commit);
                    } else {
                        // 3) Different author or too far apart — flush and start new bundle
                        result.add(mergeBundle(currentBundle));
                        currentBundle.clear();
                        currentBundle.add(commit);
                    }
                }
            }
        }

        if (!currentBundle.isEmpty()) {
            result.add(mergeBundle(currentBundle));
        }

        return result;
    }

    /**
     * Merges multiple small commits into a single bundled commit.
     */
    private RawCommitData mergeBundle(List<RawCommitData> bundle) {
        if (bundle.size() == 1) {
            return bundle.get(0);
        }

        RawCommitData first = bundle.get(0);
        List<FileChange> allChanges = bundle.stream()
                .flatMap(c -> c.fileChanges().stream())
                .collect(Collectors.toList());
        String combinedMessage = bundle.stream()
                .map(RawCommitData::message)
                .collect(Collectors.joining(" | "));

        boolean anyRename = bundle.stream().anyMatch(RawCommitData::renameDetected);
        boolean allFormatOnly = bundle.stream().allMatch(RawCommitData::formatOnly);
        boolean anyMassReformat = bundle.stream().anyMatch(RawCommitData::massReformatFlag);

        return new RawCommitData(
                first.sha(), first.authorId(), first.authorEmail(),
                combinedMessage, first.timestamp(), allChanges,
                anyRename, allFormatOnly, anyMassReformat);
    }

    // ── Chunking ─────────────────────────────────────────────────────────

    /**
     * Chunks a single commit into {@code ≤500 LoC} pieces.
     */
    private List<CommitChunkDTO> chunkCommit(RawCommitData commit) {
        if (commit.totalLinesChanged() <= MAX_LINES_PER_CHUNK) {
            return List.of(createChunk(commit, commit.fileChanges(), 0, 1));
        }

        // 1) Split files across chunks up to the line limit
        List<CommitChunkDTO> chunks = new ArrayList<>();
        List<FileChange> currentChunkFiles = new ArrayList<>();
        int currentChunkLines = 0;
        int chunkIndex = 0;

        for (FileChange file : commit.fileChanges()) {
            if (currentChunkLines + file.totalLines() > MAX_LINES_PER_CHUNK && !currentChunkFiles.isEmpty()) {
                chunks.add(createChunk(commit, currentChunkFiles, chunkIndex, -1));
                chunkIndex++;
                currentChunkFiles = new ArrayList<>();
                currentChunkLines = 0;
            }
            currentChunkFiles.add(file);
            currentChunkLines += file.totalLines();
        }
        if (!currentChunkFiles.isEmpty()) {
            chunks.add(createChunk(commit, currentChunkFiles, chunkIndex, -1));
        }

        // 2) Fix totalChunks in all chunks
        int totalChunks = chunks.size();
        return chunks.stream()
                .map(c -> new CommitChunkDTO(
                        c.commitSha(), c.authorId(), c.authorEmail(), c.commitMessage(),
                        c.timestamp(), c.files(), c.diffContent(), c.linesAdded(), c.linesDeleted(),
                        c.chunkIndex(), totalChunks, c.isBundled(), c.bundledCommits(),
                        c.renameDetected(), c.formatOnly(), c.massReformatFlag()))
                .collect(Collectors.toList());
    }

    private CommitChunkDTO createChunk(RawCommitData commit, List<FileChange> files,
            int chunkIndex, int totalChunks) {
        List<String> filePaths = files.stream().map(FileChange::filePath).collect(Collectors.toList());
        String combinedDiff = files.stream().map(FileChange::diffContent).collect(Collectors.joining("\n"));
        int linesAdded = files.stream().mapToInt(FileChange::linesAdded).sum();
        int linesDeleted = files.stream().mapToInt(FileChange::linesDeleted).sum();

        return new CommitChunkDTO(
                commit.sha(), commit.authorId(), commit.authorEmail(), commit.message(),
                commit.timestamp(), filePaths, combinedDiff, linesAdded, linesDeleted,
                chunkIndex, totalChunks, false, List.of(),
                commit.renameDetected(), commit.formatOnly(), commit.massReformatFlag());
    }
}

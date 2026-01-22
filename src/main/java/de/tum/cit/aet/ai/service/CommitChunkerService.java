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
 * Service for chunking commits for LLM-based effort analysis.
 *
 * Responsibilities:
 * - Split large commits (>500 LoC) into smaller chunks
 * - Bundle small commits (≤30 LoC) from the same author within 60 minutes
 * - Extract diff content for each chunk
 */
@Service
@Slf4j
public class CommitChunkerService {

    private static final int MAX_LINES_PER_CHUNK = 500;
    private static final int SMALL_COMMIT_THRESHOLD = 30;
    private static final int BUNDLE_TIME_WINDOW_MINUTES = 60;

    /**
     * Represents raw commit data before chunking.
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
            return fileChanges.stream()
                    .mapToInt(f -> f.linesAdded + f.linesDeleted)
                    .sum();
        }

        /**
         * Constructor without flags (for backwards compatibility).
         */
        public RawCommitData(String sha, Long authorId, String authorEmail, String message,
                             LocalDateTime timestamp, List<FileChange> fileChanges) {
            this(sha, authorId, authorEmail, message, timestamp, fileChanges, false, false, false);
        }
    }

    /**
     * Represents changes to a single file within a commit.
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

        /**
         * Constructor without detection flags (for backwards compatibility).
         */
        public FileChange(String filePath, String diffContent, int linesAdded, int linesDeleted) {
            this(filePath, diffContent, linesAdded, linesDeleted, false, 0);
        }
    }

    /**
     * Processes a repository and returns chunked commits ready for LLM analysis.
     *
     * @param localPath      Path to the cloned repository
     * @param commitToAuthor Map of commit SHA to author ID
     * @return List of commit chunks
     */
    public List<CommitChunkDTO> processRepository(String localPath, Map<String, Long> commitToAuthor) {
        log.info("Processing repository for chunking: {}", localPath);

        try {
            List<RawCommitData> rawCommits = extractCommitData(localPath, commitToAuthor);
            log.info("Extracted {} raw commits", rawCommits.size());

            // Sort by timestamp for bundling
            rawCommits.sort(Comparator.comparing(RawCommitData::timestamp));

            // Bundle small commits
            List<RawCommitData> processedCommits = bundleSmallCommits(rawCommits);
            log.info("After bundling: {} commit groups", processedCommits.size());

            // Chunk large commits and convert to DTOs
            List<CommitChunkDTO> chunks = new ArrayList<>();
            for (RawCommitData commit : processedCommits) {
                chunks.addAll(chunkCommit(commit));
            }

            log.info("Total chunks created: {}", chunks.size());
            return chunks;

        } catch (IOException e) {
            log.error("Error processing repository: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Extracts raw commit data from a Git repository.
     * Includes detection of renames, format-only changes, and mass reformats.
     */
    private List<RawCommitData> extractCommitData(String localPath, Map<String, Long> commitToAuthor)
            throws IOException {
        List<RawCommitData> commits = new ArrayList<>();
        File gitDir = new File(localPath, ".git");

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .build();
                RevWalk revWalk = new RevWalk(repository);
                DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream());
                DiffFormatter dfWhitespace = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            df.setRepository(repository);
            df.setDetectRenames(true);

            // Whitespace-ignoring formatter for format-only detection
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

                // Extract file changes with rename detection
                List<FileChange> fileChanges = extractFileChanges(repository, df, dfWhitespace, parent, commit);

                LocalDateTime timestamp = LocalDateTime.ofEpochSecond(
                        commit.getCommitTime(), 0, ZoneOffset.UTC);

                String authorEmail = commit.getAuthorIdent().getEmailAddress();

                // Detect commit-level flags
                boolean renameDetected = fileChanges.stream().anyMatch(FileChange::isRename);
                boolean formatOnly = isFormatOnlyCommit(fileChanges);
                boolean massReformat = isMassReformatCommit(fileChanges);

                commits.add(new RawCommitData(
                        commitHash,
                        authorId,
                        authorEmail,
                        commit.getShortMessage(),
                        timestamp,
                        fileChanges,
                        renameDetected,
                        formatOnly,
                        massReformat));
            }
        }

        return commits;
    }

    /**
     * Extracts file-level changes for a commit with rename and whitespace detection.
     *
     * @param repository     Git repository
     * @param df             Standard diff formatter
     * @param dfWhitespace   Whitespace-ignoring diff formatter
     * @param parent         Parent commit (null for initial commit)
     * @param commit         The commit to analyze
     * @return List of file changes with detection flags
     */
    private List<FileChange> extractFileChanges(Repository repository, DiffFormatter df,
            DiffFormatter dfWhitespace, RevCommit parent, RevCommit commit)
            throws IOException {
        List<FileChange> changes = new ArrayList<>();
        List<DiffEntry> diffs = df.scan(parent, commit);

        // Get whitespace-ignoring diffs for comparison
        List<DiffEntry> wsIgnoredDiffs = dfWhitespace.scan(parent, commit);
        Map<String, Integer> wsIgnoredLines = new HashMap<>();
        for (DiffEntry wsDiff : wsIgnoredDiffs) {
            try {
                FileHeader wsFh = dfWhitespace.toFileHeader(wsDiff);
                int wsLines = 0;
                for (Edit edit : wsFh.toEditList()) {
                    wsLines += edit.getLengthA() + edit.getLengthB();
                }
                String path = wsDiff.getNewPath().equals("/dev/null") ? wsDiff.getOldPath() : wsDiff.getNewPath();
                wsIgnoredLines.put(path, wsLines);
            } catch (Exception e) {
                // Ignore errors in whitespace comparison
            }
        }

        for (DiffEntry diff : diffs) {
            // Skip binary files and very large files
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

            String filePath = diff.getNewPath().equals("/dev/null")
                    ? diff.getOldPath()
                    : diff.getNewPath();

            // Detect rename (RENAME or COPY change type)
            boolean isRename = diff.getChangeType() == DiffEntry.ChangeType.RENAME
                    || diff.getChangeType() == DiffEntry.ChangeType.COPY;

            // Calculate whitespace-only lines
            // If ws-ignored diff has fewer changes, the difference is whitespace-only
            int totalLines = linesAdded + linesDeleted;
            int wsIgnored = wsIgnoredLines.getOrDefault(filePath, totalLines);
            int whitespaceOnlyLines = Math.max(0, totalLines - wsIgnored);

            changes.add(new FileChange(
                    filePath,
                    diffOutput.toString(),
                    linesAdded,
                    linesDeleted,
                    isRename,
                    whitespaceOnlyLines));
        }

        return changes;
    }

    /**
     * Checks if a commit only contains formatting/whitespace changes.
     * True if >80% of all changes are whitespace-only.
     */
    private boolean isFormatOnlyCommit(List<FileChange> changes) {
        if (changes.isEmpty()) {
            return false;
        }

        int totalLines = changes.stream().mapToInt(FileChange::totalLines).sum();
        int whitespaceLines = changes.stream().mapToInt(FileChange::whitespaceOnlyLines).sum();

        if (totalLines == 0) {
            return false;
        }

        // If >80% of changes are whitespace-only, it's a format commit
        return (double) whitespaceLines / totalLines > 0.8;
    }

    /**
     * Checks if a commit is a mass reformat (many files with small uniform changes).
     * True if ≥10 files and average lines per file <5.
     */
    private boolean isMassReformatCommit(List<FileChange> changes) {
        if (changes.size() < 10) {
            return false;
        }

        int totalLines = changes.stream().mapToInt(FileChange::totalLines).sum();
        double avgLinesPerFile = (double) totalLines / changes.size();

        return avgLinesPerFile < 5.0;
    }

    /**
     * Checks if a file should be skipped (generated, dependencies, etc.)
     */
    private boolean shouldSkipFile(String filePath) {
        if (filePath == null || filePath.equals("/dev/null")) {
            return false;
        }

        // Skip common generated/dependency paths
        String[] skipPatterns = {
                "node_modules/", "vendor/", "target/", "build/", ".gradle/",
                "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                ".min.js", ".min.css", ".map",
                ".class", ".jar", ".war",
                ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
                ".woff", ".woff2", ".ttf", ".eot"
        };

        for (String pattern : skipPatterns) {
            if (filePath.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Bundles small commits from the same author within 60 minutes.
     */
    private List<RawCommitData> bundleSmallCommits(List<RawCommitData> commits) {
        if (commits.isEmpty()) {
            return commits;
        }

        List<RawCommitData> result = new ArrayList<>();
        List<RawCommitData> currentBundle = new ArrayList<>();

        for (RawCommitData commit : commits) {
            if (commit.totalLinesChanged() > SMALL_COMMIT_THRESHOLD) {
                // Large commit - flush bundle and add as-is
                if (!currentBundle.isEmpty()) {
                    result.add(mergeBundle(currentBundle));
                    currentBundle.clear();
                }
                result.add(commit);
            } else {
                // Small commit - try to bundle
                if (currentBundle.isEmpty()) {
                    currentBundle.add(commit);
                } else {
                    RawCommitData lastInBundle = currentBundle.get(currentBundle.size() - 1);

                    boolean sameAuthor = Objects.equals(commit.authorId(), lastInBundle.authorId());
                    long minutesDiff = Duration.between(lastInBundle.timestamp(), commit.timestamp()).toMinutes();

                    if (sameAuthor && minutesDiff <= BUNDLE_TIME_WINDOW_MINUTES) {
                        currentBundle.add(commit);
                    } else {
                        // Different author or too far apart - flush and start new bundle
                        result.add(mergeBundle(currentBundle));
                        currentBundle.clear();
                        currentBundle.add(commit);
                    }
                }
            }
        }

        // Don't forget the last bundle
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

        // Merge file changes
        List<FileChange> allChanges = bundle.stream()
                .flatMap(c -> c.fileChanges().stream())
                .collect(Collectors.toList());

        // Concatenate messages
        String combinedMessage = bundle.stream()
                .map(RawCommitData::message)
                .collect(Collectors.joining(" | "));

        // Merge flags: true if any commit has the flag
        boolean anyRename = bundle.stream().anyMatch(RawCommitData::renameDetected);
        boolean anyFormatOnly = bundle.stream().allMatch(RawCommitData::formatOnly); // All must be format-only
        boolean anyMassReformat = bundle.stream().anyMatch(RawCommitData::massReformatFlag);

        log.debug("Bundled {} commits from author {}", bundle.size(), first.authorId());

        return new RawCommitData(
                first.sha(), // Use first SHA as identifier
                first.authorId(),
                first.authorEmail(),
                combinedMessage,
                first.timestamp(), // Use earliest timestamp
                allChanges,
                anyRename,
                anyFormatOnly,
                anyMassReformat);
    }

    /**
     * Chunks a single commit into smaller pieces if necessary.
     */
    private List<CommitChunkDTO> chunkCommit(RawCommitData commit) {
        int totalLines = commit.totalLinesChanged();

        if (totalLines <= MAX_LINES_PER_CHUNK) {
            // Small enough - return as single chunk
            return List.of(createChunk(commit, commit.fileChanges(), 0, 1, false));
        }

        // Need to split into multiple chunks
        List<CommitChunkDTO> chunks = new ArrayList<>();
        List<FileChange> currentChunkFiles = new ArrayList<>();
        int currentChunkLines = 0;
        int chunkIndex = 0;

        for (FileChange file : commit.fileChanges()) {
            if (currentChunkLines + file.totalLines() > MAX_LINES_PER_CHUNK && !currentChunkFiles.isEmpty()) {
                // Current chunk is full, start a new one
                chunks.add(createChunk(commit, currentChunkFiles, chunkIndex, -1, false)); // -1 = unknown total yet
                chunkIndex++;
                currentChunkFiles = new ArrayList<>();
                currentChunkLines = 0;
            }

            currentChunkFiles.add(file);
            currentChunkLines += file.totalLines();
        }

        // Add the last chunk
        if (!currentChunkFiles.isEmpty()) {
            chunks.add(createChunk(commit, currentChunkFiles, chunkIndex, -1, false));
        }

        // Update total chunks count
        int totalChunks = chunks.size();
        return chunks.stream()
                .map(c -> new CommitChunkDTO(
                        c.commitSha(), c.authorId(), c.authorEmail(), c.commitMessage(),
                        c.timestamp(), c.files(), c.diffContent(), c.linesAdded(), c.linesDeleted(),
                        c.chunkIndex(), totalChunks, c.isBundled(), c.bundledCommits(),
                        c.renameDetected(), c.formatOnly(), c.massReformatFlag()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a CommitChunkDTO from file changes.
     */
    private CommitChunkDTO createChunk(
            RawCommitData commit,
            List<FileChange> files,
            int chunkIndex,
            int totalChunks,
            boolean isBundled) {
        List<String> filePaths = files.stream()
                .map(FileChange::filePath)
                .collect(Collectors.toList());

        String combinedDiff = files.stream()
                .map(FileChange::diffContent)
                .collect(Collectors.joining("\n"));

        int linesAdded = files.stream().mapToInt(FileChange::linesAdded).sum();
        int linesDeleted = files.stream().mapToInt(FileChange::linesDeleted).sum();

        return new CommitChunkDTO(
                commit.sha(),
                commit.authorId(),
                commit.authorEmail(),
                commit.message(),
                commit.timestamp(),
                filePaths,
                combinedDiff,
                linesAdded,
                linesDeleted,
                chunkIndex,
                totalChunks,
                isBundled,
                List.of(),
                commit.renameDetected(),
                commit.formatOnly(),
                commit.massReformatFlag());
    }
}

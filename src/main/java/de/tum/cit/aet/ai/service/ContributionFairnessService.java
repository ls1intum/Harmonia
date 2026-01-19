package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.domain.FairnessFlag;
import de.tum.cit.aet.ai.dto.*;

import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator service for contribution fairness analysis.
 * Integrates Git analysis, commit chunking, and LLM effort rating to generate a
 * fairness report.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContributionFairnessService {

    private final CommitChunkerService commitChunkerService;
    private final CommitEffortRaterService commitEffortRaterService;
    // private final GitContributionAnalysisService gitContributionAnalysisService;
    // // Not used yet in MVP logic

    // Thresholds
    private static final double WARNING_THRESHOLD_SHARE = 0.70; // 70% share warning
    private static final double CRITICAL_THRESHOLD_SHARE = 0.85; // 85% share critical
    private static final double TRIVIAL_RATIO_THRESHOLD = 0.40; // 40% trivial commits
    // private static final double LATE_WORK_THRESHOLD = 0.50; // Not used yet

    /**
     * Analyzes a repository for contribution fairness.
     *
     * @param repositoryDTO the repository data transfer object to analyze
     * @return a FairnessReportDTO containing the analysis results
     */
    public FairnessReportDTO analyzeFairness(TeamRepositoryDTO repositoryDTO) {
        String repoPath = repositoryDTO.localPath();
        String teamName = repositoryDTO.participation().team().name();

        if (repoPath == null) {
            log.warn("Repository not cloned locally for team {}", teamName);
            return FairnessReportDTO.error(teamName, "Repository not cloned locally");
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. Map commits to authors
            Map<String, Long> commitToAuthor = mapCommitsToAuthors(repositoryDTO.vcsLogs());

            if (commitToAuthor.isEmpty()) {
                log.warn("No commits found for team {} - VCS logs may be empty", teamName);
                return FairnessReportDTO.error(teamName, "No commits found in VCS logs");
            }

            // 2. Chunk and bundle commits
            List<CommitChunkDTO> chunks = commitChunkerService.processRepository(repoPath, commitToAuthor);

            if (chunks.isEmpty()) {
                log.warn("No valid chunks created for team {} - all commits may have been filtered", teamName);
                return FairnessReportDTO.error(teamName, "No analyzable code changes found");
            }

            // 3. Rate effort for each chunk (individual errors are handled in rateChunks)
            List<RatedChunk> ratedChunks = rateChunks(chunks);

            // 4. Aggregate results per author
            Map<Long, AuthorStats> authorStats = aggregateStats(ratedChunks);

            // 5. Calculate global metrics
            double totalEffort = authorStats.values().stream().mapToDouble(s -> s.totalEffort).sum();
            Map<Long, Double> effortShare = calculateShares(authorStats, totalEffort);
            double balanceScore = calculateBalanceScore(effortShare);

            // 6. Generate flags
            List<FairnessFlag> flags = generateFlags(effortShare, authorStats, totalEffort);

            // 7. Build report
            long duration = System.currentTimeMillis() - startTime;
            log.info("Fairness analysis completed for team {}: score={}, chunks={}, duration={}ms",
                    teamName, balanceScore, chunks.size(), duration);

            return buildReport(
                    teamName,
                    balanceScore,
                    authorStats,
                    effortShare,
                    flags,
                    chunks.size(),
                    ratedChunks,
                    duration);

        } catch (Exception e) {
            log.error("Fairness analysis failed for team {}: {}", teamName, e.getMessage(), e);
            return FairnessReportDTO.error(teamName, "Analysis error: " + e.getMessage());
        }
    }

    private Map<String, Long> mapCommitsToAuthors(List<VCSLogDTO> logs) {
        Map<String, Long> mapping = new HashMap<>();

        // Build synthetic author IDs from email addresses
        // In a real implementation, this would look up actual user IDs from the
        // database
        Map<String, Long> emailToSyntheticId = new HashMap<>();
        long idCounter = 1;

        for (VCSLogDTO log : logs) {
            if (log.commitHash() == null || log.email() == null) {
                continue;
            }

            // Get or create a synthetic ID for this email
            Long authorId = emailToSyntheticId.get(log.email());
            if (authorId == null) {
                authorId = idCounter++;
                emailToSyntheticId.put(log.email(), authorId);
            }

            // Map commit hash to author ID
            mapping.put(log.commitHash(), authorId);
        }

        log.debug("Mapped {} commits to {} unique authors", mapping.size(), emailToSyntheticId.size());
        return mapping;
    }

    private List<RatedChunk> rateChunks(List<CommitChunkDTO> chunks) {
        return chunks.stream()
                .map(chunk -> new RatedChunk(chunk, commitEffortRaterService.rateChunk(chunk)))
                .toList();
    }

    private Map<Long, AuthorStats> aggregateStats(List<RatedChunk> ratedChunks) {
        Map<Long, AuthorStats> stats = new HashMap<>();

        for (RatedChunk rc : ratedChunks) {
            AuthorStats s = stats.computeIfAbsent(rc.chunk.authorId(), k -> new AuthorStats());
            double weightedEffort = rc.rating.weightedEffort();

            s.totalEffort += weightedEffort;
            s.chunkCount++;
            s.commitsByType.merge(rc.rating.type(), 1, Integer::sum);
            s.email = rc.chunk.authorEmail(); // Capture email

            if (rc.rating.confidence() < 0.7) {
                s.lowConfidenceCount++;
            }
        }

        return stats;
    }

    private Map<Long, Double> calculateShares(Map<Long, AuthorStats> stats, double totalEffort) {
        Map<Long, Double> shares = new HashMap<>();
        if (totalEffort == 0) {
            return shares;
        }

        stats.forEach((id, stat) -> shares.put(id, stat.totalEffort / totalEffort));
        return shares;
    }

    private double calculateBalanceScore(Map<Long, Double> shares) {
        if (shares.isEmpty()) {
            return 0.0;
        }
        if (shares.size() == 1) {
            return 0.0; // Solo dev = 0 balance
        }

        // Simple Gini-like metric for 2 people: 100 * (1 - |diff|)
        // Ideally works for n people using standard deviation
        double[] values = shares.values().stream().mapToDouble(d -> d).toArray();
        double stdev = calculateStDev(values);
        double maxStdev = Math.sqrt(shares.size() - 1.0) / shares.size(); // Rough approx

        // If maxStdev is 0 (shouldn't happen with size > 1) prevent NaN
        if (maxStdev == 0) {
            return 100.0;
        }

        // Normalized score
        double score = 100.0 * (1.0 - (stdev / 0.5)); // 0.5 is max possible stdev for 2 people (0.0, 1.0)
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double calculateStDev(double[] values) {
        double mean = 1.0 / values.length; // Shares sum to 1
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / values.length);
    }

    private List<FairnessFlag> generateFlags(Map<Long, Double> shares, Map<Long, AuthorStats> stats,
            double totalEffort) {
        List<FairnessFlag> flags = new ArrayList<>();

        // Distribution checks
        for (double share : shares.values()) {
            if (share > CRITICAL_THRESHOLD_SHARE) {
                flags.add(FairnessFlag.SOLO_CONTRIBUTOR);
                break; // Critical implies uneven
            } else if (share > WARNING_THRESHOLD_SHARE) {
                if (!flags.contains(FairnessFlag.SOLO_CONTRIBUTOR)) {
                    flags.add(FairnessFlag.UNEVEN_DISTRIBUTION);
                }
            }
        }

        // Triviality checks
        for (AuthorStats s : stats.values()) {
            int trivial = s.commitsByType.getOrDefault(CommitLabel.TRIVIAL, 0);
            if (s.chunkCount > 5 && (double) trivial / s.chunkCount > TRIVIAL_RATIO_THRESHOLD) {
                flags.add(FairnessFlag.HIGH_TRIVIAL_RATIO);
                break;
            }
        }

        return flags;
    }

    private FairnessReportDTO buildReport(
            String teamId,
            double score,
            Map<Long, AuthorStats> stats,
            Map<Long, Double> shares,
            List<FairnessFlag> flags,
            int totalChunks,
            List<RatedChunk> ratedChunks,
            long duration) {
        List<FairnessReportDTO.AuthorDetailDTO> details = new ArrayList<>();

        stats.forEach((id, stat) -> {
            details.add(new FairnessReportDTO.AuthorDetailDTO(
                    id,
                    stat.email,
                    stat.totalEffort,
                    shares.getOrDefault(id, 0.0),
                    stat.commitsByType.values().stream().mapToInt(i -> i).sum(), // Appox commit count
                    stat.chunkCount,
                    stat.chunkCount > 0 ? stat.totalEffort / stat.chunkCount : 0,
                    stat.commitsByType));
        });

        // Metadata
        int lowConf = stats.values().stream().mapToInt(s -> s.lowConfidenceCount).sum();
        double avgConf = ratedChunks.stream().mapToDouble(c -> c.rating.confidence()).average().orElse(0.0);

        FairnessReportDTO.AnalysisMetadataDTO metadata = new FairnessReportDTO.AnalysisMetadataDTO(
                ratedChunks.size(), // Total commits approx
                totalChunks,
                0, // Bundled count tracked elsewhere or calculated
                avgConf,
                lowConf,
                duration);

        // Convert RatedChunks to AnalyzedChunkDTOs for frontend
        List<AnalyzedChunkDTO> analyzedChunks = ratedChunks.stream()
                .map(rc -> new AnalyzedChunkDTO(
                        rc.chunk.commitSha(),
                        rc.chunk.authorEmail(),
                        rc.chunk.authorEmail(), // Use email as name placeholder
                        rc.rating.type().name(),
                        rc.rating.weightedEffort(),
                        rc.rating.reasoning(),
                        List.of(rc.chunk.commitSha()),
                        List.of(rc.chunk.commitMessage()),
                        rc.chunk.timestamp(),
                        rc.chunk.linesAdded() + rc.chunk.linesDeleted(),
                        rc.chunk.isBundled(),
                        rc.chunk.chunkIndex(),
                        rc.chunk.totalChunks(),
                        rc.rating.isError(),
                        rc.rating.errorMessage()))
                .collect(Collectors.toList());

        return new FairnessReportDTO(
                teamId,
                score,
                stats.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().totalEffort)),
                shares,
                flags,
                !flags.isEmpty(),
                details,
                metadata,
                analyzedChunks);
    }

    // --- Helper Classes ---

    // Using record would be cleaner but inner class works for aggregator state
    private static class AuthorStats {
        String email;
        double totalEffort = 0;
        int chunkCount = 0;
        int lowConfidenceCount = 0;
        Map<CommitLabel, Integer> commitsByType = new HashMap<>();
    }

    private record RatedChunk(CommitChunkDTO chunk, EffortRatingDTO rating) {
    }
}

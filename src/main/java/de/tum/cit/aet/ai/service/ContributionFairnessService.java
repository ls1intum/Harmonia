package de.tum.cit.aet.ai.service;

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
     */
    public FairnessReportDTO analyzeFairness(TeamRepositoryDTO repositoryDTO) {
        String repoPath = repositoryDTO.localPath();
        if (repoPath == null) {
            return FairnessReportDTO.error("unknown", "Repository not cloned locally");
        }

        long startTime = System.currentTimeMillis();

        // 1. Map commits to authors
        Map<String, Long> commitToAuthor = mapCommitsToAuthors(repositoryDTO.vcsLogs());

        // 2. Chunk and bundle commits
        List<CommitChunkDTO> chunks = commitChunkerService.processRepository(repoPath, commitToAuthor);

        // 3. Rate effort for each chunk
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
        return buildReport(
                repositoryDTO.participation().team().name(), // simplistic team ID
                balanceScore,
                authorStats,
                effortShare,
                flags,
                chunks.size(),
                ratedChunks,
                duration);
    }

    private Map<String, Long> mapCommitsToAuthors(List<VCSLogDTO> logs) {
        // This logic mimics what GitContributionAnalysisService does internally
        // In a real scenario, we might want to reuse a shared mapping service
        // For now, we assume we can map via email if available, or just map all logs we
        // have
        Map<String, Long> mapping = new HashMap<>();

        // Basic mapping logic - this obviously needs the
        // GitContributionAnalysisService's
        // sophisticated logic to match emails to student IDs.
        // For this MVP, we rely on what we can get or assume the DTO passed valid
        // mapping data if extended.
        // HACK: We assume VCSLogDTO might eventually carry author ID or we do a lookup.
        // Since we don't have direct access to user DB here easily without injection
        // circularity,
        // we might need to rely on the passed DTO structure updates in future.
        // FOR NOW: We just use a dummy mapping based on 'email' hash or similar if ID
        // missing,
        // but practically we need real IDs.

        // Let's assume we can't fully map without DB access.
        // We will try to rely on what GitContributionAnalysisService provides or just
        // map available logs.
        for (VCSLogDTO log : logs) {
            // Placeholder: In real app, resolved Author ID is needed.
            // mapping.put(log.commitHash(), resolvedAuthorId);
            // Simulating basic mapping for now if we don't have IDs in DTO
            // Real implementation requires user service or map passed in
        }
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
        if (totalEffort == 0)
            return shares;

        stats.forEach((id, stat) -> shares.put(id, stat.totalEffort / totalEffort));
        return shares;
    }

    private double calculateBalanceScore(Map<Long, Double> shares) {
        if (shares.isEmpty())
            return 0.0;
        if (shares.size() == 1)
            return 0.0; // Solo dev = 0 balance

        // Simple Gini-like metric for 2 people: 100 * (1 - |diff|)
        // Ideally works for n people using standard deviation
        double[] values = shares.values().stream().mapToDouble(d -> d).toArray();
        double stdev = calculateStDev(values);
        double maxStdev = Math.sqrt(shares.size() - 1.0) / shares.size(); // Rough approx

        // If maxStdev is 0 (shouldn't happen with size > 1) prevent NaN
        if (maxStdev == 0)
            return 100.0;

        // Normalized score
        double score = 100.0 * (1.0 - (stdev / 0.5)); // 0.5 is max possible stdev for 2 people (0.0, 1.0)
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double calculateStDev(double[] values) {
        double mean = 1.0 / values.length; // Shares sum to 1
        double sumSq = 0.0;
        for (double v : values)
            sumSq += Math.pow(v - mean, 2);
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

        return new FairnessReportDTO(
                teamId,
                score,
                stats.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().totalEffort)),
                shares,
                flags,
                !flags.isEmpty(),
                details,
                metadata);
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

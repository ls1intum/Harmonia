package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.domain.FairnessFlag;
import de.tum.cit.aet.ai.dto.*;

import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator service for contribution fairness analysis.
 * Integrates Git analysis, commit chunking, pre-filtering, LLM effort rating,
 * and CQI calculation to generate a fairness report.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContributionFairnessService {

    private final CommitChunkerService commitChunkerService;
    private final CommitEffortRaterService commitEffortRaterService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;

    /**
     * Analyzes a repository for contribution fairness.
     * Uses pre-filtering, LLM rating, and CQI calculation.
     *
     * @param repositoryDTO the repository data transfer object to analyze
     * @return a FairnessReportDTO containing the analysis results
     */
    public FairnessReportDTO analyzeFairness(TeamRepositoryDTO repositoryDTO) {
        String repoPath = repositoryDTO.localPath();
        String teamName = repositoryDTO.participation().team().name();
        int teamSize = repositoryDTO.participation().team().students().size();

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
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repoPath, commitToAuthor);

            if (allChunks.isEmpty()) {
                log.warn("No valid chunks created for team {} - all commits may have been filtered", teamName);
                return FairnessReportDTO.error(teamName, "No analyzable code changes found");
            }

            // 3. PRE-FILTER: Remove trivial commits BEFORE LLM analysis (saves API costs)
            CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);
            List<CommitChunkDTO> chunksToAnalyze = filterResult.chunksToAnalyze();
            FilterSummaryDTO filterSummary = filterResult.summary();

            log.info("Pre-filter for team {}: {} of {} commits will be analyzed (saved {}%)",
                    teamName, chunksToAnalyze.size(), allChunks.size(),
                    String.format("%.0f", filterSummary.getFilteredPercentage()));

            if (chunksToAnalyze.isEmpty()) {
                log.warn("All commits filtered for team {} - no productive work detected", teamName);
                return FairnessReportDTO.error(teamName, "All commits were filtered as non-productive");
            }

            // 4. Rate effort for each chunk with LLM (only non-trivial commits)
            List<RatedChunk> ratedChunks = rateChunks(chunksToAnalyze);

            // 5. Calculate CQI using the 4-component formula
            LocalDateTime projectStart = chunksToAnalyze.stream()
                    .map(CommitChunkDTO::timestamp)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().minusDays(30));

            LocalDateTime projectEnd = chunksToAnalyze.stream()
                    .map(CommitChunkDTO::timestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            // Convert to CQI RatedChunks
            List<CQICalculatorService.RatedChunk> cqiRatedChunks = ratedChunks.stream()
                    .map(rc -> new CQICalculatorService.RatedChunk(rc.chunk, rc.rating))
                    .toList();

            CQIResultDTO cqiResult = cqiCalculatorService.calculate(
                    cqiRatedChunks,
                    teamSize,
                    projectStart,
                    projectEnd,
                    filterSummary
            );

            double balanceScore = cqiResult.cqi();
            log.info("CQI calculated for team {}: {} (base={}, penalty={})",
                    teamName, String.format("%.1f", balanceScore),
                    String.format("%.1f", cqiResult.baseScore()),
                    String.format("%.2f", cqiResult.penaltyMultiplier()));

            // 6. Aggregate stats for report (legacy compatibility)
            Map<Long, AuthorStats> authorStats = aggregateStats(ratedChunks);
            double totalEffort = authorStats.values().stream().mapToDouble(s -> s.totalEffort).sum();
            Map<Long, Double> effortShare = calculateShares(authorStats, totalEffort);

            // 7. Generate flags from CQI penalties
            List<FairnessFlag> flags = generateFlagsFromCQI(cqiResult, effortShare);

            // 8. Build report
            long duration = System.currentTimeMillis() - startTime;
            log.info("Fairness analysis completed for team {}: CQI={}, chunks={}, filtered={}, duration={}ms",
                    teamName, String.format("%.1f", balanceScore), chunksToAnalyze.size(),
                    filterSummary.getTotalFiltered(), duration);

            return buildReport(
                    teamName,
                    balanceScore,
                    authorStats,
                    effortShare,
                    flags,
                    allChunks.size(),
                    ratedChunks,
                    duration,
                    cqiResult);

        } catch (Exception e) {
            log.error("Fairness analysis failed for team {}: {}", teamName, e.getMessage(), e);
            return FairnessReportDTO.error(teamName, "Analysis error: " + e.getMessage());
        }
    }

    /**
     * Generate fairness flags from CQI penalties.
     */
    private List<FairnessFlag> generateFlagsFromCQI(CQIResultDTO cqiResult, Map<Long, Double> effortShare) {
        List<FairnessFlag> flags = new ArrayList<>();

        if (cqiResult.penalties() != null) {
            for (var penalty : cqiResult.penalties()) {
                switch (penalty.type()) {
                    case "SOLO_DEVELOPMENT" -> flags.add(FairnessFlag.SOLO_CONTRIBUTOR);
                    case "SEVERE_IMBALANCE" -> flags.add(FairnessFlag.UNEVEN_DISTRIBUTION);
                    case "HIGH_TRIVIAL_RATIO" -> flags.add(FairnessFlag.HIGH_TRIVIAL_RATIO);
                    case "LOW_CONFIDENCE" -> flags.add(FairnessFlag.LOW_CONFIDENCE);
                    case "LATE_WORK" -> flags.add(FairnessFlag.LATE_WORK);
                    default -> log.debug("Unknown penalty type: {}", penalty.type());
                }
            }
        }

        // Deduplicate flags
        return new ArrayList<>(new LinkedHashSet<>(flags));
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

    private FairnessReportDTO buildReport(
            String teamId,
            double score,
            Map<Long, AuthorStats> stats,
            Map<Long, Double> shares,
            List<FairnessFlag> flags,
            int totalChunks,
            List<RatedChunk> ratedChunks,
            long duration,
            CQIResultDTO cqiResult) {
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

        // Convert RatedChunks to AnalyzedChunkDTOs for client
        List<AnalyzedChunkDTO> analyzedChunks = ratedChunks.stream()
                .map(rc -> new AnalyzedChunkDTO(
                        rc.chunk.commitSha(),
                        rc.chunk.authorEmail(),
                        rc.chunk.authorEmail(), // Use email as name placeholder
                        rc.rating.type().name(),
                        rc.rating.weightedEffort(),
                        rc.rating.complexity(),
                        rc.rating.novelty(),
                        rc.rating.confidence(),
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
                analyzedChunks,
                cqiResult);
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

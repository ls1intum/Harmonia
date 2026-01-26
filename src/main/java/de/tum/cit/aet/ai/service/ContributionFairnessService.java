package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.domain.FairnessFlag;
import de.tum.cit.aet.ai.dto.*;

import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
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
     * Only commits from registered team members are included in CQI calculation.
     *
     * @param repositoryDTO the repository data transfer object to analyze
     * @return a FairnessReportDTO containing the analysis results
     */
    public FairnessReportDTO analyzeFairness(TeamRepositoryDTO repositoryDTO) {
        String repoPath = repositoryDTO.localPath();
        String teamName = repositoryDTO.participation().team().name();
        int teamSize = repositoryDTO.participation().team().students().size();
        List<ParticipantDTO> teamMembers = repositoryDTO.participation().team().students();

        if (repoPath == null) {
            return FairnessReportDTO.error(teamName, "Repository not cloned locally");
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. Map commits to authors (only team members for CQI)
            AuthorMappingResult authorMapping = mapCommitsToAuthors(repositoryDTO.vcsLogs(), teamMembers);

            if (authorMapping.teamMemberCommits.isEmpty()) {
                return FairnessReportDTO.error(teamName, "No commits found from team members in VCS logs");
            }
            
            if (!authorMapping.externalCommitEmails.isEmpty()) {
                log.info("Team {}: Found {} external contributor(s): {}", 
                        teamName, authorMapping.externalCommitEmails.size(), authorMapping.externalCommitEmails);
            }

            // 2. Chunk and bundle commits (team members only for CQI calculation)
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repoPath, authorMapping.teamMemberCommits);
            
            // 2b. Also chunk external contributor commits (for display only, not CQI)
            List<CommitChunkDTO> externalChunks = Collections.emptyList();
            if (!authorMapping.externalCommits.isEmpty()) {
                externalChunks = commitChunkerService.processRepository(repoPath, authorMapping.externalCommits);
                log.info("Team {}: {} external contributor chunks identified", teamName, externalChunks.size());
            }

            if (allChunks.isEmpty()) {
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
            
            // 7b. Rate external chunks with trivial rating (for display only)
            List<RatedChunk> externalRatedChunks = externalChunks.stream()
                    .map(chunk -> new RatedChunk(chunk, EffortRatingDTO.trivial("External contributor")))
                    .toList();

            // 8. Build report
            long duration = System.currentTimeMillis() - startTime;
            log.info("Fairness analysis completed for team {}: CQI={}, chunks={}, filtered={}, external={}, duration={}ms",
                    teamName, String.format("%.1f", balanceScore), chunksToAnalyze.size(),
                    filterSummary.getTotalFiltered(), externalChunks.size(), duration);

            return buildReport(
                    teamName,
                    balanceScore,
                    authorStats,
                    effortShare,
                    flags,
                    allChunks.size(),
                    ratedChunks,
                    externalRatedChunks,
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

    /**
     * Normalizes TUM email addresses to a canonical form for matching.
     * TUM uses multiple domains: @tum.de, @mytum.de, @in.tum.de, @cit.tum.de, etc.
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String lower = email.toLowerCase().trim();
        
        int atIndex = lower.indexOf('@');
        if (atIndex <= 0) {
            return lower;
        }
        
        String localPart = lower.substring(0, atIndex);
        String domain = lower.substring(atIndex + 1);
        
        // Normalize TUM domains to canonical form
        if (domain.endsWith("tum.de") || domain.equals("mytum.de")) {
            return localPart + "@tum.de";
        }
        
        return lower;
    }

    /**
     * Result of author mapping, separating team member commits from external commits.
     */
    private record AuthorMappingResult(
            Map<String, Long> teamMemberCommits,
            Map<String, Long> externalCommits,
            Set<String> externalCommitEmails
    ) {}

    /**
     * Maps commits to authors, separating team member commits from external contributor commits.
     * Only commits from registered team members are included in the main mapping for CQI calculation.
     */
    private AuthorMappingResult mapCommitsToAuthors(List<VCSLogDTO> logs, List<ParticipantDTO> teamMembers) {
        Map<String, Long> teamMemberCommits = new HashMap<>();
        Map<String, Long> externalCommits = new HashMap<>();
        Set<String> externalEmails = new HashSet<>();

        // Build a lookup map of team member emails to their IDs (using normalized emails)
        Map<String, Long> teamMemberEmailToId = new HashMap<>();
        for (ParticipantDTO member : teamMembers) {
            if (member.email() != null) {
                String normalized = normalizeEmail(member.email());
                teamMemberEmailToId.put(normalized, member.id());
                teamMemberEmailToId.put(member.email().toLowerCase(), member.id());
            }
        }
        
        log.debug("Team members (normalized): {}", teamMemberEmailToId.keySet());

        // Track synthetic IDs for external contributors
        Map<String, Long> externalEmailToId = new HashMap<>();
        long externalIdCounter = -1;

        for (VCSLogDTO vcsLog : logs) {
            if (vcsLog.commitHash() == null || vcsLog.email() == null) {
                continue;
            }

            String normalizedEmail = normalizeEmail(vcsLog.email());
            String originalEmail = vcsLog.email().toLowerCase();
            
            // Try normalized email first, then original
            Long teamMemberId = teamMemberEmailToId.get(normalizedEmail);
            if (teamMemberId == null) {
                teamMemberId = teamMemberEmailToId.get(originalEmail);
            }
            
            if (teamMemberId != null) {
                teamMemberCommits.put(vcsLog.commitHash(), teamMemberId);
            } else {
                // External contributor
                Long externalId = externalEmailToId.get(originalEmail);
                if (externalId == null) {
                    externalId = externalIdCounter--;
                    externalEmailToId.put(originalEmail, externalId);
                    externalEmails.add(vcsLog.email());
                }
                externalCommits.put(vcsLog.commitHash(), externalId);
            }
        }

        log.debug("Mapped {} team member commits and {} external commits from {} external contributors", 
                teamMemberCommits.size(), externalCommits.size(), externalEmails.size());
        return new AuthorMappingResult(teamMemberCommits, externalCommits, externalEmails);
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
            List<RatedChunk> externalRatedChunks,
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

        // Convert RatedChunks to AnalyzedChunkDTOs for client (team members)
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
                        rc.rating.errorMessage(),
                        false)) // isExternalContributor = false
                .collect(Collectors.toList());
        
        // Add external contributor chunks (marked with isExternalContributor = true)
        List<AnalyzedChunkDTO> externalChunks = externalRatedChunks.stream()
                .map(rc -> new AnalyzedChunkDTO(
                        rc.chunk.commitSha(),
                        rc.chunk.authorEmail(),
                        rc.chunk.authorEmail(),
                        "EXTERNAL",
                        0.0, // No effort contribution to CQI
                        0.0, 0.0, 0.0,
                        "External contributor - not included in CQI calculation",
                        List.of(rc.chunk.commitSha()),
                        List.of(rc.chunk.commitMessage()),
                        rc.chunk.timestamp(),
                        rc.chunk.linesAdded() + rc.chunk.linesDeleted(),
                        rc.chunk.isBundled(),
                        rc.chunk.chunkIndex(),
                        rc.chunk.totalChunks(),
                        false, null,
                        true)) // isExternalContributor = true
                .toList();
        
        // Combine team member and external chunks
        analyzedChunks.addAll(externalChunks);

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

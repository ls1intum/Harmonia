package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.*;

import de.tum.cit.aet.analysis.dto.cqi.*;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.dto.CommitMappingResultDTO;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full contribution fairness analysis pipeline for a team repository.
 * <p>
 * Pipeline steps:
 * <ol>
 *   <li>Map commits to authors via git history walk</li>
 *   <li>Chunk and bundle commits</li>
 *   <li>Pre-filter trivial commits (saves LLM costs)</li>
 *   <li>Rate effort per chunk with LLM</li>
 *   <li>Calculate CQI score</li>
 *   <li>Build fairness report</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContributionFairnessService {

    private final CommitChunkerService commitChunkerService;
    private final CommitEffortRaterService commitEffortRaterService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final GitContributionAnalysisService gitContributionAnalysisService;

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Analyses a repository for contribution fairness.
     *
     * @param repositoryDTO the repository to analyse
     * @return fairness report
     */
    public FairnessReportDTO analyzeFairness(TeamRepositoryDTO repositoryDTO) {
        return analyzeFairnessWithUsage(repositoryDTO).report();
    }

    /**
     * Analyses a repository and returns both fairness report and LLM token totals.
     *
     * @param repositoryDTO the repository to analyse
     * @return fairness report plus token usage
     */
    public FairnessReportWithUsageDTO analyzeFairnessWithUsage(TeamRepositoryDTO repositoryDTO) {
        return analyzeFairnessWithUsage(repositoryDTO, null);
    }

    /**
     * Analyses a repository with optional template author exclusion.
     *
     * @param repositoryDTO       the repository to analyse
     * @param templateAuthorEmail email of the template author to exclude (lowercase), or {@code null}
     * @return fairness report plus token usage
     */
    public FairnessReportWithUsageDTO analyzeFairnessWithUsage(TeamRepositoryDTO repositoryDTO,
            String templateAuthorEmail) {
        String repoPath = repositoryDTO.localPath();
        String teamName = repositoryDTO.participation().team().name();
        int teamSize = repositoryDTO.participation().team().students().size();
        List<ParticipantDTO> teamMembers = repositoryDTO.participation().team().students();
        LlmTokenTotalsDTO teamTokenTotals = LlmTokenTotalsDTO.empty();

        if (repoPath == null) {
            return new FairnessReportWithUsageDTO(
                    FairnessReportDTO.error(teamName, "Repository not cloned locally"),
                    teamTokenTotals);
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1) Map commits to authors using full git history walk
            AuthorMappingResult authorMapping = mapCommitsToAuthors(repositoryDTO, templateAuthorEmail);

            if (authorMapping.teamMemberCommits.isEmpty()) {
                return new FairnessReportWithUsageDTO(
                        FairnessReportDTO.error(teamName, "No commits found from team members in repository history"),
                        teamTokenTotals);
            }

            // 2) Chunk and bundle commits (team members only for CQI)
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(
                    repoPath, authorMapping.teamMemberCommits, authorMapping.commitToEmail);

            List<CommitChunkDTO> externalChunks = Collections.emptyList();
            if (!authorMapping.externalCommits.isEmpty()) {
                externalChunks = commitChunkerService.processRepository(
                        repoPath, authorMapping.externalCommits, authorMapping.commitToEmail);
            }

            if (allChunks.isEmpty()) {
                return new FairnessReportWithUsageDTO(
                        FairnessReportDTO.error(teamName, "No analyzable code changes found"),
                        teamTokenTotals);
            }

            // 3) Pre-filter trivial commits before LLM analysis
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
            List<CommitChunkDTO> chunksToAnalyze = filterResult.chunksToAnalyze();
            FilterSummaryDTO filterSummary = filterResult.summary();

            if (chunksToAnalyze.isEmpty()) {
                return new FairnessReportWithUsageDTO(
                        FairnessReportDTO.error(teamName, "All commits were filtered as non-productive"),
                        teamTokenTotals);
            }

            // 4) Rate effort for each chunk with LLM
            RatedChunksWithUsageDTO ratedChunksWithUsage = rateChunks(chunksToAnalyze);
            List<RatedChunkDTO> ratedChunks = ratedChunksWithUsage.ratedChunks();
            teamTokenTotals = ratedChunksWithUsage.tokenTotals();

            // 5) Calculate CQI score
            LocalDateTime projectStart = chunksToAnalyze.stream()
                    .map(CommitChunkDTO::timestamp).filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().minusDays(30));
            LocalDateTime projectEnd = chunksToAnalyze.stream()
                    .map(CommitChunkDTO::timestamp).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            List<CqiRatedChunkDTO> cqiRatedChunks = ratedChunks.stream()
                    .map(rc -> new CqiRatedChunkDTO(rc.chunk(), rc.rating()))
                    .toList();

            CQIResultDTO cqiResult = cqiCalculatorService.calculate(
                    cqiRatedChunks, teamSize, projectStart, projectEnd, filterSummary, teamName);

            // 6) Aggregate author stats
            Map<Long, AuthorStats> authorStats = aggregateStats(ratedChunks);
            double totalEffort = authorStats.values().stream().mapToDouble(s -> s.totalEffort).sum();
            Map<Long, Double> effortShare = calculateShares(authorStats, totalEffort);

            // 7) Rate external contributor chunks (for display, not CQI)
            List<RatedChunkDTO> externalRatedChunks = rateExternalChunks(externalChunks);
            if (!externalChunks.isEmpty()) {
                teamTokenTotals = recalculateExternalTokens(teamTokenTotals, externalRatedChunks);
            }

            // 8) Build report
            long duration = System.currentTimeMillis() - startTime;

            Map<String, String> emailToName = buildEmailToNameMap(teamMembers);
            FairnessReportDTO report = buildReport(teamName, cqiResult.cqi(), authorStats, effortShare,
                    allChunks.size(), ratedChunks, externalRatedChunks, duration, cqiResult, emailToName);

            return new FairnessReportWithUsageDTO(report, teamTokenTotals);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FairnessReportWithUsageDTO(
                    FairnessReportDTO.error(teamName, "Analysis cancelled"), teamTokenTotals);
        } catch (Exception e) {
            log.error("Fairness analysis failed for team {}: {}", teamName, e.getMessage(), e);
            return new FairnessReportWithUsageDTO(
                    FairnessReportDTO.error(teamName, "Analysis error: " + e.getMessage()), teamTokenTotals);
        }
    }

    // ── Author mapping ───────────────────────────────────────────────────

    private record AuthorMappingResult(
            Map<String, Long> teamMemberCommits,
            Map<String, Long> externalCommits,
            Set<String> externalCommitEmails,
            Map<String, String> commitToEmail
    ) {
    }

    /**
     * Maps commits to authors, separating team member commits from external/orphan commits.
     * Delegates the git history walk to {@link GitContributionAnalysisService#mapCommitToAuthor}
     * and assigns synthetic negative IDs for external contributors (grouped by email).
     */
    private AuthorMappingResult mapCommitsToAuthors(TeamRepositoryDTO repositoryDTO,
            String templateAuthorEmail) {
        // 1) Delegate commit mapping to analysis service (single git walk)
        CommitMappingResultDTO mapping = gitContributionAnalysisService.mapCommitToAuthor(
                repositoryDTO, templateAuthorEmail);

        // 2) Filter assigned commits to team members only
        Set<Long> teamMemberIds = new HashSet<>();
        for (ParticipantDTO member : repositoryDTO.participation().team().students()) {
            if (member.id() != null) {
                teamMemberIds.add(member.id());
            }
        }

        Map<String, Long> teamMemberCommits = new HashMap<>();
        for (Map.Entry<String, Long> entry : mapping.commitToAuthor().entrySet()) {
            if (teamMemberIds.contains(entry.getValue())) {
                teamMemberCommits.put(entry.getKey(), entry.getValue());
            }
        }

        // 3) Assign synthetic negative IDs to orphan commits (grouped by email)
        Map<String, Long> externalCommits = new HashMap<>();
        Set<String> externalEmails = new HashSet<>();
        Map<String, Long> emailToExternalId = new HashMap<>();
        long externalIdCounter = -1;

        for (Map.Entry<String, String> orphanEntry : mapping.orphanCommitEmails().entrySet()) {
            String orphanHash = orphanEntry.getKey();
            String email = orphanEntry.getValue() != null ? orphanEntry.getValue() : "unknown";
            Long externalId = emailToExternalId.get(email);
            if (externalId == null) {
                externalId = externalIdCounter--;
                emailToExternalId.put(email, externalId);
                externalEmails.add(email);
            }
            externalCommits.put(orphanHash, externalId);
        }

        return new AuthorMappingResult(teamMemberCommits, externalCommits, externalEmails,
                new HashMap<>(mapping.commitToEmail()));
    }

    // ── Chunk rating ─────────────────────────────────────────────────────

    private RatedChunksWithUsageDTO rateChunks(List<CommitChunkDTO> chunks) throws InterruptedException {
        List<RatedChunkDTO> ratedChunks = new ArrayList<>();
        LlmTokenTotalsDTO tokenTotals = LlmTokenTotalsDTO.empty();

        for (CommitChunkDTO chunk : chunks) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Analysis cancelled during chunk rating");
            }
            CommitEffortRaterService.RatingWithUsage ratingWithUsage =
                    commitEffortRaterService.rateChunkWithUsage(chunk);
            ratedChunks.add(new RatedChunkDTO(chunk, ratingWithUsage.rating(), ratingWithUsage.tokenUsage()));
            tokenTotals = tokenTotals.addUsage(ratingWithUsage.tokenUsage());
        }

        return new RatedChunksWithUsageDTO(ratedChunks, tokenTotals);
    }

    /**
     * Rates external contributor chunks with full LLM analysis.
     * Scores are stored for later CQI recalculation when manually mapped to a student.
     */
    private List<RatedChunkDTO> rateExternalChunks(List<CommitChunkDTO> externalChunks)
            throws InterruptedException {
        if (externalChunks.isEmpty()) {
            return List.of();
        }

        PreFilterResultDTO extFilter = commitPreFilterService.preFilter(externalChunks);

        // 1) Pre-filtered chunks get trivial rating
        List<RatedChunkDTO> trivialRated = extFilter.filteredChunks().stream()
                .map(pfc -> new RatedChunkDTO(pfc.chunk(),
                        EffortRatingDTO.trivial("Pre-filtered external commit"),
                        LlmTokenUsageDTO.unavailable("external-filtered")))
                .toList();

        // 2) Remaining chunks get full LLM analysis
        if (!extFilter.chunksToAnalyze().isEmpty()) {
            RatedChunksWithUsageDTO externalRated = rateChunks(extFilter.chunksToAnalyze());
            List<RatedChunkDTO> combined = new ArrayList<>(externalRated.ratedChunks());
            combined.addAll(trivialRated);
            return combined;
        }

        return trivialRated;
    }

    /**
     * Recalculates team token totals after adding external chunk ratings.
     */
    private LlmTokenTotalsDTO recalculateExternalTokens(LlmTokenTotalsDTO base, List<RatedChunkDTO> externalRated) {
        LlmTokenTotalsDTO external = LlmTokenTotalsDTO.empty();
        for (RatedChunkDTO rc : externalRated) {
            external = external.addUsage(rc.tokenUsage());
        }
        return base.merge(external);
    }

    // ── Stats aggregation ────────────────────────────────────────────────

    private Map<Long, AuthorStats> aggregateStats(List<RatedChunkDTO> ratedChunks) {
        Map<Long, AuthorStats> stats = new HashMap<>();
        for (RatedChunkDTO rc : ratedChunks) {
            AuthorStats s = stats.computeIfAbsent(rc.chunk().authorId(), _ -> new AuthorStats());
            s.totalEffort += rc.rating().weightedEffort();
            s.chunkCount++;
            s.commitsByType.merge(rc.rating().type(), 1, Integer::sum);
            s.email = rc.chunk().authorEmail();
            if (rc.rating().confidence() < 0.7) {
                s.lowConfidenceCount++;
            }
        }
        return stats;
    }

    private Map<Long, Double> calculateShares(Map<Long, AuthorStats> stats, double totalEffort) {
        if (totalEffort == 0) {
            return Map.of();
        }
        Map<Long, Double> shares = new HashMap<>();
        stats.forEach((id, stat) -> shares.put(id, stat.totalEffort / totalEffort));
        return shares;
    }

    // ── Report building ──────────────────────────────────────────────────

    private Map<String, String> buildEmailToNameMap(List<ParticipantDTO> teamMembers) {
        Map<String, String> emailToName = new HashMap<>();
        for (ParticipantDTO member : teamMembers) {
            if (member.email() != null && member.name() != null) {
                emailToName.put(member.email().toLowerCase(), member.name());
            }
        }
        return emailToName;
    }

    private FairnessReportDTO buildReport(
            String teamId, double score,
            Map<Long, AuthorStats> stats, Map<Long, Double> shares,
            int totalChunks, List<RatedChunkDTO> ratedChunks,
            List<RatedChunkDTO> externalRatedChunks, long duration,
            CQIResultDTO cqiResult, Map<String, String> emailToName) {

        // 1) Author details
        List<FairnessReportDTO.AuthorDetailDTO> details = new ArrayList<>();
        stats.forEach((id, stat) -> details.add(new FairnessReportDTO.AuthorDetailDTO(
                id, stat.email, stat.totalEffort, shares.getOrDefault(id, 0.0),
                stat.commitsByType.values().stream().mapToInt(i -> i).sum(),
                stat.chunkCount,
                stat.chunkCount > 0 ? stat.totalEffort / stat.chunkCount : 0,
                stat.commitsByType)));

        // 2) Analysis metadata
        int lowConf = stats.values().stream().mapToInt(s -> s.lowConfidenceCount).sum();
        double avgConf = ratedChunks.stream().mapToDouble(c -> c.rating().confidence()).average().orElse(0.0);
        FairnessReportDTO.AnalysisMetadataDTO metadata = new FairnessReportDTO.AnalysisMetadataDTO(
                ratedChunks.size(), totalChunks, 0, avgConf, lowConf, duration);

        // 3) Convert rated chunks to client DTOs
        List<AnalyzedChunkDTO> analyzedChunks = ratedChunks.stream()
                .map(rc -> toAnalyzedChunkDTO(rc, emailToName, false))
                .collect(Collectors.toList());
        List<AnalyzedChunkDTO> externalAnalyzed = externalRatedChunks.stream()
                .map(rc -> toAnalyzedChunkDTO(rc, emailToName, true))
                .toList();
        analyzedChunks.addAll(externalAnalyzed);

        return new FairnessReportDTO(
                teamId, score,
                stats.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().totalEffort)),
                shares, false, details, metadata, analyzedChunks, cqiResult);
    }

    private AnalyzedChunkDTO toAnalyzedChunkDTO(RatedChunkDTO rc, Map<String, String> emailToName,
            boolean isExternal) {
        return new AnalyzedChunkDTO(
                rc.chunk().commitSha(),
                rc.chunk().authorEmail(),
                emailToName.getOrDefault(rc.chunk().authorEmail().toLowerCase(), rc.chunk().authorEmail()),
                rc.rating().type().name(),
                rc.rating().weightedEffort(),
                rc.rating().complexity(),
                rc.rating().novelty(),
                rc.rating().confidence(),
                rc.rating().reasoning(),
                List.of(rc.chunk().commitSha()),
                List.of(rc.chunk().commitMessage()),
                rc.chunk().timestamp(),
                rc.chunk().linesAdded() + rc.chunk().linesDeleted(),
                rc.chunk().isBundled(),
                rc.chunk().chunkIndex(),
                rc.chunk().totalChunks(),
                rc.rating().isError(),
                rc.rating().errorMessage(),
                isExternal,
                rc.tokenUsage());
    }

    // ── Inner types ──────────────────────────────────────────────────────

    private static class AuthorStats {
        String email;
        double totalEffort = 0;
        int chunkCount = 0;
        int lowConfidenceCount = 0;
        Map<CommitLabel, Integer> commitsByType = new HashMap<>();
    }

}

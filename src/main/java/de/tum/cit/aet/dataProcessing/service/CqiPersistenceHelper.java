package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentWeightsDTO;
import de.tum.cit.aet.analysis.dto.cqi.PreFilterResultDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.Tutor;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Helper service for CQI calculation, persistence, and token usage logging.
 */
@Service
@Slf4j
public class CqiPersistenceHelper {

    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final CommitChunkerService commitChunkerService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final ContributionBalanceCalculator balanceCalculator;
    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final AnalysisQueryService queryService;

    public CqiPersistenceHelper(
            GitContributionAnalysisService gitContributionAnalysisService,
            CommitChunkerService commitChunkerService,
            CommitPreFilterService commitPreFilterService,
            CQICalculatorService cqiCalculatorService,
            ContributionBalanceCalculator balanceCalculator,
            TeamParticipationRepository teamParticipationRepository,
            StudentRepository studentRepository,
            AnalysisQueryService queryService) {
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.commitChunkerService = commitChunkerService;
        this.commitPreFilterService = commitPreFilterService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.balanceCalculator = balanceCalculator;
        this.teamParticipationRepository = teamParticipationRepository;
        this.studentRepository = studentRepository;
        this.queryService = queryService;
    }

    /**
     * Calculates git-only CQI components (without effort balance) for a team.
     *
     * @param repo              the cloned repository
     * @param teamParticipation the team participation entity
     * @param team              the team DTO
     * @param students          the persisted student entities
     * @return CQI result with git-only components, or {@code null} on failure
     */
    public CQIResultDTO calculateGitOnlyCqi(TeamRepositoryDTO repo, TeamParticipation teamParticipation,
                                             TeamDTO team, List<Student> students) {
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);

            ComponentScoresDTO gitComponents = cqiCalculatorService.calculateGitOnlyComponents(
                    filterResult.chunksToAnalyze(), students.size(), null, null, team.name());

            if (gitComponents != null) {
                teamParticipation.setCqiLocBalance(gitComponents.locBalance());
                teamParticipation.setCqiTemporalSpread(gitComponents.temporalSpread());
                teamParticipation.setCqiOwnershipSpread(gitComponents.ownershipSpread());
                teamParticipation.setCqiPairProgramming(gitComponents.pairProgramming());
                teamParticipation.setCqiPairProgrammingStatus(gitComponents.pairProgrammingStatus());
                teamParticipationRepository.save(teamParticipation);

                return CQIResultDTO.gitOnly(cqiCalculatorService.buildWeightsDTO(), gitComponents, filterResult.summary());
            }
        } catch (Exception e) {
            log.warn("Failed to calculate git-only metrics for team {}: {}", team.name(), e.getMessage());
        }
        return null;
    }

    /**
     * Calculates fallback CQI using pre-filtered commit chunks when fairness analysis fails.
     *
     * @param repo     the cloned repository
     * @param team     the team DTO
     * @param students the persisted student entities
     * @return CQI result, or {@code null} on failure
     */
    public CQIResultDTO calculateFallbackCqi(TeamRepositoryDTO repo, TeamDTO team, List<Student> students) {
        if (repo.localPath() == null) {
            return null;
        }
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
            return cqiCalculatorService.calculateFallback(
                    filterResult.chunksToAnalyze(), students.size(), filterResult.summary());
        } catch (Exception e) {
            log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Persists CQI component scores onto a team participation entity.
     *
     * @param teamParticipation the team participation to update
     * @param cqiDetails        the CQI result containing component scores
     */
    public void persistCqiComponents(TeamParticipation teamParticipation, CQIResultDTO cqiDetails) {
        if (cqiDetails == null || cqiDetails.components() == null) {
            return;
        }
        teamParticipation.setCqiEffortBalance(cqiDetails.components().effortBalance());
        teamParticipation.setCqiLocBalance(cqiDetails.components().locBalance());
        teamParticipation.setCqiTemporalSpread(cqiDetails.components().temporalSpread());
        teamParticipation.setCqiOwnershipSpread(cqiDetails.components().ownershipSpread());
        teamParticipation.setCqiBaseScore(cqiDetails.baseScore());
    }

    /**
     * Calculates a git-only CQI (without effort balance) for SIMPLE mode and persists the result.
     *
     * @param participation the Artemis participation DTO
     * @param repo          the cloned repository
     * @param exerciseId    the exercise being analyzed
     * @return client-facing response DTO with the simple CQI
     */
    public ClientResponseDTO calculateAndPersistSimpleCqi(ParticipationDTO participation,
                                                           TeamRepositoryDTO repo, Long exerciseId) {
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for simple CQI"));

        List<Student> students = studentRepository.findAllByTeam(teamParticipation);

        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        Double cqi = null;
        CQIResultDTO simpleCqiDetails = gitCqiDetails;
        if (gitCqiDetails != null && gitCqiDetails.components() != null) {
            ComponentWeightsDTO weights = cqiCalculatorService.buildWeightsDTO();
            double wLoc = weights.locBalance();
            double wTemporal = weights.temporalSpread();
            double wOwnership = weights.ownershipSpread();
            double divisor = wLoc + wTemporal + wOwnership;

            if (divisor > 0) {
                double locScore = gitCqiDetails.components().locBalance();
                double temporalScore = gitCqiDetails.components().temporalSpread();
                double ownershipScore = gitCqiDetails.components().ownershipSpread();

                double rawCqi = (wLoc * locScore + wTemporal * temporalScore + wOwnership * ownershipScore) / divisor;
                cqi = (double) Math.max(0, Math.min(100, Math.round(rawCqi)));
            }

            simpleCqiDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails);
        }

        persistCqiComponents(teamParticipation, gitCqiDetails);
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(false);
        teamParticipation.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();
        CQIResultDTO finalDetails = simpleCqiDetails != null ? simpleCqiDetails : queryService.reconstructCqiDetails(teamParticipation, AnalysisMode.SIMPLE);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), team.name(), participation.submissionCount(),
                studentDtos, cqi, false, de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus.DONE,
                finalDetails, null, null, null, null, null);
    }

    /**
     * Writes aggregated LLM token usage totals onto the team participation entity.
     *
     * @param tp     the team participation to update
     * @param totals the token totals, or {@code null} to clear
     */
    public void persistTeamTokenTotals(TeamParticipation tp, LlmTokenTotalsDTO totals) {
        if (totals == null) {
            tp.setLlmCalls(null);
            tp.setLlmCallsWithUsage(null);
            tp.setLlmPromptTokens(null);
            tp.setLlmCompletionTokens(null);
            tp.setLlmTotalTokens(null);
            return;
        }
        tp.setLlmCalls(totals.llmCalls());
        tp.setLlmCallsWithUsage(totals.callsWithUsage());
        tp.setLlmPromptTokens(totals.promptTokens());
        tp.setLlmCompletionTokens(totals.completionTokens());
        tp.setLlmTotalTokens(totals.totalTokens());
    }

    /**
     * Logs aggregated LLM token usage for an analysis run.
     *
     * @param scope         descriptive label for the log entry (e.g. "FULL" or "SIMPLE")
     * @param exerciseId    the exercise that was analyzed
     * @param analyzedTeams number of teams analyzed
     * @param tokenTotals   the aggregated token usage
     */
    public void logTotalUsage(String scope, Long exerciseId, int analyzedTeams, LlmTokenTotalsDTO tokenTotals) {
        log.info("LLM_USAGE scope={} exerciseId={} teams={} llmCalls={} promptTokens={} completionTokens={} totalTokens={}",
                scope, exerciseId, analyzedTeams,
                tokenTotals.llmCalls(), tokenTotals.promptTokens(),
                tokenTotals.completionTokens(), tokenTotals.totalTokens());
    }
}

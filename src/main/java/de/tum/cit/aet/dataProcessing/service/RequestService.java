package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Facade that delegates to specialized services.
 *
 * <ul>
 *   <li>Task lifecycle    → {@link AnalysisTaskManager}</li>
 *   <li>Queries           → {@link AnalysisQueryService}</li>
 *   <li>Pair programming  → {@link PairProgrammingMetricsService}</li>
 *   <li>Cleanup           → {@link ExerciseDataCleanupService}</li>
 *   <li>Persistence       → {@link AnalysisResultPersistenceService}</li>
 *   <li>Pipeline          → {@link StreamingAnalysisPipelineService}</li>
 * </ul>
 */
@Service
public class RequestService {

    private final AnalysisTaskManager analysisTaskManager;
    private final AnalysisQueryService analysisQueryService;
    private final PairProgrammingMetricsService pairProgrammingMetricsService;
    private final ExerciseDataCleanupService exerciseDataCleanupService;
    private final AnalysisResultPersistenceService persistenceService;
    private final StreamingAnalysisPipelineService pipelineService;

    public RequestService(
            AnalysisTaskManager analysisTaskManager,
            AnalysisQueryService analysisQueryService,
            PairProgrammingMetricsService pairProgrammingMetricsService,
            ExerciseDataCleanupService exerciseDataCleanupService,
            AnalysisResultPersistenceService persistenceService,
            StreamingAnalysisPipelineService pipelineService) {
        this.analysisTaskManager = analysisTaskManager;
        this.analysisQueryService = analysisQueryService;
        this.pairProgrammingMetricsService = pairProgrammingMetricsService;
        this.exerciseDataCleanupService = exerciseDataCleanupService;
        this.persistenceService = persistenceService;
        this.pipelineService = pipelineService;
    }

    // =====================================================================
    //  Task lifecycle — delegates to AnalysisTaskManager
    // =====================================================================

    /**
     * Stops a running analysis task for the given exercise.
     *
     * @param exerciseId the exercise id
     */
    public void stopAnalysis(Long exerciseId) {
        analysisTaskManager.stopAnalysis(exerciseId);
    }

    /**
     * Checks whether an analysis task is currently running.
     *
     * @param exerciseId the exercise id
     * @return {@code true} if running
     */
    public boolean isTaskRunning(Long exerciseId) {
        return analysisTaskManager.isTaskRunning(exerciseId);
    }

    /**
     * Registers a running analysis task.
     *
     * @param exerciseId the exercise id
     * @param future     the task future
     */
    public void registerRunningTask(Long exerciseId, Future<?> future) {
        analysisTaskManager.registerRunningTask(exerciseId, future);
    }

    /**
     * Unregisters a completed analysis task.
     *
     * @param exerciseId the exercise id
     */
    public void unregisterRunningTask(Long exerciseId) {
        analysisTaskManager.unregisterRunningTask(exerciseId);
    }

    // =====================================================================
    //  Query methods — delegates to AnalysisQueryService
    // =====================================================================

    /**
     * Returns all team results for an exercise.
     *
     * @param exerciseId the exercise id
     * @return list of responses
     */
    public List<ClientResponseDTO> getTeamsByExerciseId(Long exerciseId) {
        return analysisQueryService.getTeamsByExerciseId(exerciseId);
    }

    /**
     * Returns analysis results for all teams across all exercises.
     *
     * @return list of responses
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        return analysisQueryService.getAllRepositoryData();
    }

    /**
     * Checks if analyzed data exists for an exercise.
     *
     * @param exerciseId the exercise id
     * @return {@code true} if data exists
     */
    public boolean hasAnalyzedDataForExercise(Long exerciseId) {
        return analysisQueryService.hasAnalyzedDataForExercise(exerciseId);
    }

    /**
     * Returns lightweight summaries for an exercise.
     *
     * @param exerciseId the exercise id
     * @return list of summaries
     */
    public List<TeamSummaryDTO> getTeamSummariesByExerciseId(Long exerciseId) {
        return analysisQueryService.getTeamSummariesByExerciseId(exerciseId);
    }

    /**
     * Returns detail for a single team.
     *
     * @param exerciseId the exercise id
     * @param teamId     the team id
     * @return the response or empty
     */
    public Optional<ClientResponseDTO> getTeamDetail(Long exerciseId, Long teamId) {
        return analysisQueryService.getTeamDetail(exerciseId, teamId);
    }

    // =====================================================================
    //  Database clearing — delegates to ExerciseDataCleanupService
    // =====================================================================

    /**
     * Clears all persisted data for an exercise.
     *
     * @param exerciseId the exercise id
     */
    @Transactional
    public void clearDatabaseForExercise(Long exerciseId) {
        exerciseDataCleanupService.clearDatabaseForExercise(exerciseId);
    }

    // =====================================================================
    //  Pair programming — delegates to PairProgrammingMetricsService
    // =====================================================================

    /**
     * Recomputes pair programming metrics for all teams in an exercise.
     *
     * @param exerciseId the exercise id
     * @return number of updated teams
     */
    @Transactional
    public int recomputePairProgrammingForExercise(Long exerciseId) {
        return pairProgrammingMetricsService.recomputePairProgrammingForExercise(exerciseId);
    }

    /**
     * Clears pair programming metrics for all teams in an exercise.
     *
     * @param exerciseId the exercise id
     * @return number of updated teams
     */
    @Transactional
    public int clearPairProgrammingForExercise(Long exerciseId) {
        return pairProgrammingMetricsService.clearPairProgrammingForExercise(exerciseId);
    }

    // =====================================================================
    //  Persistence — delegates to AnalysisResultPersistenceService
    // =====================================================================

    /**
     * Persists git analysis results (full mode).
     *
     * @param repo             the repo
     * @param contributionData contributions
     * @param exerciseId       the exercise id
     * @return client response
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        return persistenceService.saveGitAnalysisResult(repo, contributionData, exerciseId);
    }

    /**
     * Persists git analysis results.
     *
     * @param repo             the repo
     * @param contributionData contributions
     * @param exerciseId       the exercise id
     * @param mode             analysis mode
     * @return client response
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData,
                                                    Long exerciseId, AnalysisMode mode) {
        return persistenceService.saveGitAnalysisResult(repo, contributionData, exerciseId, mode);
    }

    /**
     * Persists AI analysis results.
     *
     * @param repo       the repo
     * @param exerciseId the exercise id
     * @return client response
     */
    public ClientResponseDTO saveAIAnalysisResult(TeamRepositoryDTO repo, Long exerciseId) {
        return persistenceService.saveAIAnalysisResult(repo, exerciseId);
    }

    /**
     * Re-runs AI analysis for a single team.
     *
     * @param exerciseId the exercise id
     * @param teamId     the team id
     * @return response or empty
     */
    public Optional<ClientResponseDTO> runSingleTeamAIAnalysis(Long exerciseId, Long teamId) {
        return persistenceService.runSingleTeamAIAnalysis(exerciseId, teamId);
    }

    /**
     * Applies stored email mappings to a team's chunks.
     *
     * @param participation the participation
     * @param exerciseId    the exercise id
     */
    @Transactional
    void applyExistingEmailMappings(TeamParticipation participation, Long exerciseId) {
        persistenceService.applyExistingEmailMappings(participation, exerciseId);
    }

    // =====================================================================
    //  Pipeline — delegates to StreamingAnalysisPipelineService
    // =====================================================================

    /**
     * Fetches, analyzes, and saves all repositories for an exercise.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  the exercise id
     */
    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId) {
        pipelineService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId);
    }

    /**
     * Fetches, analyzes, and saves repositories with a team limit.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  the exercise id
     * @param maxTeams    maximum teams
     * @return list of responses
     */
    public List<ClientResponseDTO> fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId,
                                                                    int maxTeams) {
        return pipelineService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId, maxTeams);
    }

    /**
     * Fetches and clones repositories without running analysis.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  the exercise id
     * @return list of cloned repos
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        return pipelineService.fetchAndCloneRepositories(credentials, exerciseId);
    }

    /**
     * Streams the analysis pipeline, emitting SSE events as teams are processed.
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   the exercise id
     * @param mode         analysis mode
     * @param eventEmitter SSE event consumer
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                       AnalysisMode mode,
                                                       Consumer<Object> eventEmitter) {
        pipelineService.fetchAnalyzeAndSaveRepositoriesStream(credentials, exerciseId, mode, eventEmitter);
    }
}

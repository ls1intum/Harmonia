package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.service.AnalysisQueryService;
import de.tum.cit.aet.analysis.service.AnalysisTaskManager;
import de.tum.cit.aet.analysis.service.AnalysisResultPersistenceService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Facade exposed to REST controllers. Delegates to domain-specific services
 * so that controllers never depend on internal service implementations directly.
 */
@Service
public class RequestService {

    private final AnalysisTaskManager analysisTaskManager;
    private final AnalysisQueryService analysisQueryService;
    private final ExerciseTeamLifecycleService exerciseDataCleanupService;
    private final AnalysisResultPersistenceService persistenceService;
    private final StreamingAnalysisPipelineService pipelineService;

    public RequestService(
            AnalysisTaskManager analysisTaskManager,
            AnalysisQueryService analysisQueryService,
            ExerciseTeamLifecycleService exerciseDataCleanupService,
            AnalysisResultPersistenceService persistenceService,
            StreamingAnalysisPipelineService pipelineService) {
        this.analysisTaskManager = analysisTaskManager;
        this.analysisQueryService = analysisQueryService;
        this.exerciseDataCleanupService = exerciseDataCleanupService;
        this.persistenceService = persistenceService;
        this.pipelineService = pipelineService;
    }

    // =====================================================================
    //  Task lifecycle
    // =====================================================================

    public void stopAnalysis(Long exerciseId) {
        analysisTaskManager.stopAnalysis(exerciseId);
    }

    public boolean isTaskRunning(Long exerciseId) {
        return analysisTaskManager.isTaskRunning(exerciseId);
    }

    public void registerRunningTask(Long exerciseId, Future<?> future) {
        analysisTaskManager.registerRunningTask(exerciseId, future);
    }

    public void unregisterRunningTask(Long exerciseId) {
        analysisTaskManager.unregisterRunningTask(exerciseId);
    }

    // =====================================================================
    //  Queries
    // =====================================================================

    public List<ClientResponseDTO> getTeamsByExerciseId(Long exerciseId) {
        return analysisQueryService.getTeamsByExerciseId(exerciseId);
    }

    public List<ClientResponseDTO> getAllRepositoryData() {
        return analysisQueryService.getAllRepositoryData();
    }

    public boolean hasAnalyzedDataForExercise(Long exerciseId) {
        return analysisQueryService.hasAnalyzedDataForExercise(exerciseId);
    }

    public List<TeamSummaryDTO> getTeamSummariesByExerciseId(Long exerciseId) {
        return analysisQueryService.getTeamSummariesByExerciseId(exerciseId);
    }

    public Optional<ClientResponseDTO> getTeamDetail(Long exerciseId, Long teamId) {
        return analysisQueryService.getTeamDetail(exerciseId, teamId);
    }

    // =====================================================================
    //  Database clearing
    // =====================================================================

    @Transactional
    public void clearDatabaseForExercise(Long exerciseId) {
        exerciseDataCleanupService.clearDatabaseForExercise(exerciseId);
    }

    // =====================================================================
    //  Persistence
    // =====================================================================

    public Optional<ClientResponseDTO> runSingleTeamAIAnalysis(Long exerciseId, Long teamId) {
        return persistenceService.runSingleTeamAIAnalysis(exerciseId, teamId);
    }

    public ClientResponseDTO toggleReviewStatus(Long exerciseId, Long teamId) {
        return persistenceService.toggleReviewStatus(exerciseId, teamId);
    }

    // =====================================================================
    //  Pipeline
    // =====================================================================

    public List<ClientResponseDTO> fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId,
                                                                    int maxTeams) {
        return pipelineService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId, maxTeams);
    }

    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        return pipelineService.fetchAndCloneRepositories(credentials, exerciseId);
    }

    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                       AnalysisMode mode,
                                                       Consumer<Object> eventEmitter) {
        pipelineService.fetchAnalyzeAndSaveRepositoriesStream(credentials, exerciseId, mode, eventEmitter);
    }

}

package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.dto.AnalysisStatusDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.dataProcessing.service.ExerciseDataCleanupService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/analysis")
@Slf4j
public class AnalysisResource {

    private final AnalysisStateService stateService;
    private final RequestService requestService;
    private final PairProgrammingService pairProgrammingService;
    private final ExerciseDataCleanupService cleanupService;

    public AnalysisResource(AnalysisStateService stateService, RequestService requestService,
                            PairProgrammingService pairProgrammingService, ExerciseDataCleanupService cleanupService) {
        this.stateService = stateService;
        this.requestService = requestService;
        this.pairProgrammingService = pairProgrammingService;
        this.cleanupService = cleanupService;
    }

    /**
     * Get the current analysis status for an exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the status DTO
     */
    @GetMapping("/{exerciseId}/status")
    public ResponseEntity<AnalysisStatusDTO> getStatus(@PathVariable Long exerciseId) {
        AnalysisStatus status = stateService.getStatus(exerciseId);
        return ResponseEntity.ok(toDTO(status));
    }

    /**
     * Cancel a running analysis. This will stop the analysis and interrupt
     * any running AI requests.
     *
     * @param exerciseId the id of the exercise
     * @return the updated status DTO
     */
    @PostMapping("/{exerciseId}/cancel")
    public ResponseEntity<AnalysisStatusDTO> cancelAnalysis(@PathVariable Long exerciseId) {
        log.info("POST cancelAnalysis for exerciseId={}", exerciseId);

        // First, stop the active executor to interrupt running threads
        requestService.stopAnalysis(exerciseId);

        // Then update the state to CANCELLED
        AnalysisStatus status = stateService.cancelAnalysis(exerciseId);
        return ResponseEntity.ok(toDTO(status));
    }

    /**
     * Clear data for an exercise.
     *
     * @param exerciseId    the id of the exercise
     * @param type          One of: "db", "files", "both"
     * @param clearMappings if true, also delete email mappings for this exercise
     * @return a response entity
     */
    @DeleteMapping("/{exerciseId}/clear")
    public ResponseEntity<String> clearData(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "both") String type,
            @RequestParam(defaultValue = "false") boolean clearMappings) {
        log.info("DELETE clearData for exerciseId={}, type={}, clearMappings={}", exerciseId, type, clearMappings);

        try {
            // First, stop any running analysis task to prevent it from continuing
            requestService.stopAnalysis(exerciseId);

            // Clear attendance data so pair programming metric won't show on next analysis
            // unless a new Excel file is uploaded
            pairProgrammingService.clear();
            log.info("Attendance data cleared for exerciseId={}", exerciseId);

            if ("db".equals(type) || "both".equals(type)) {
                requestService.clearDatabaseForExercise(exerciseId);
                stateService.resetStatus(exerciseId);
                log.info("Database cleared for exerciseId={}", exerciseId);

                if (clearMappings) {
                    cleanupService.clearEmailMappingsAndTemplateAuthor(exerciseId);
                }
            }

            if ("files".equals(type) || "both".equals(type)) {
                cleanupService.clearRepositoryFiles();
                log.info("Repository files cleared for exerciseId={}", exerciseId);
            }

            return ResponseEntity.ok("Data cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear data for exerciseId={}", exerciseId, e);
            return ResponseEntity.internalServerError().body("Failed to clear data: " + e.getMessage());
        }
    }

    /**
     * Run AI analysis for a single team on demand.
     *
     * @param exerciseId the exercise ID
     * @param teamId     the Artemis team ID
     * @return the updated team data, or 404 if not found
     */
    @PostMapping("/{exerciseId}/teams/{teamId}/compute-ai")
    public ResponseEntity<ClientResponseDTO> computeAiForTeam(
            @PathVariable Long exerciseId, @PathVariable Long teamId) {
        log.info("POST computeAiForTeam for exerciseId={}, teamId={}", exerciseId, teamId);
        return requestService.runSingleTeamAIAnalysis(exerciseId, teamId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private AnalysisStatusDTO toDTO(AnalysisStatus status) {
        return new AnalysisStatusDTO(
                status.getExerciseId(),
                status.getState().name(),
                status.getTotalTeams(),
                status.getProcessedTeams(),
                status.getCurrentTeamName(),
                status.getCurrentStage(),
                status.getStartedAt(),
                status.getLastUpdatedAt(),
                status.getErrorMessage(),
                status.getAnalysisMode() != null ? status.getAnalysisMode().name() : null);
    }
}

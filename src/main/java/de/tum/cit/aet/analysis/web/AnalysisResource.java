package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.dto.AnalysisStatusDTO;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@RestController
@RequestMapping("api/analysis")
@Slf4j
public class AnalysisResource {

    private final AnalysisStateService stateService;
    private final RequestService requestService;
    private final PairProgrammingService pairProgrammingService;
    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;

    public AnalysisResource(AnalysisStateService stateService, RequestService requestService,
                            PairProgrammingService pairProgrammingService, ExerciseEmailMappingRepository emailMappingRepository,
                            ExerciseTemplateAuthorRepository templateAuthorRepository) {
        this.stateService = stateService;
        this.requestService = requestService;
        this.pairProgrammingService = pairProgrammingService;
        this.emailMappingRepository = emailMappingRepository;
        this.templateAuthorRepository = templateAuthorRepository;
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
                    emailMappingRepository.deleteAllByExerciseId(exerciseId);
                    templateAuthorRepository.deleteByExerciseId(exerciseId);
                    log.info("Email mappings and template author cleared for exerciseId={}", exerciseId);
                }
            }

            if ("files".equals(type) || "both".equals(type)) {
                clearRepositoryFiles();
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

    private void clearRepositoryFiles() {
        // Clear ~/.harmonia/repos
        Path reposDir = Paths.get(System.getProperty("user.home"), ".harmonia", "repos");
        deleteDirectoryContents(reposDir, "~/.harmonia/repos");

        // Clear Projects folder (relative to working directory)
        Path projectsDir = Paths.get("Projects");
        deleteDirectoryContents(projectsDir, "Projects");
    }

    private void deleteDirectoryContents(Path dir, String dirName) {
        if (Files.exists(dir)) {
            log.info("Clearing directory: {}", dir.toAbsolutePath());
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(dir)) // Keep the root directory itself
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete {}: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.error("Failed to walk directory {}: {}", dirName, e.getMessage());
            }
            log.info("Cleared {} successfully", dirName);
        } else {
            log.info("Directory {} does not exist, nothing to clear", dirName);
        }
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

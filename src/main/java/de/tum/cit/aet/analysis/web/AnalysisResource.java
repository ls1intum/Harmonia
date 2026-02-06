package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.dto.AnalysisStatusDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
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

    public AnalysisResource(AnalysisStateService stateService, RequestService requestService) {
        this.stateService = stateService;
        this.requestService = requestService;
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
        log.info("Cancel requested for exercise: {}", exerciseId);

        // First, stop the active executor to interrupt running threads
        requestService.stopAnalysis(exerciseId);

        // Then update the state to CANCELLED
        AnalysisStatus status = stateService.cancelAnalysis(exerciseId);
        return ResponseEntity.ok(toDTO(status));
    }

    /**
     * Clear data for an exercise.
     *
     * @param exerciseId the id of the exercise
     * @param type       One of: "db", "files", "both"
     * @return a response entity
     */
    @DeleteMapping("/{exerciseId}/clear")
    public ResponseEntity<String> clearData(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "both") String type) {
        log.info("Clear requested for exercise: {}, type: {}", exerciseId, type);

        try {
            if ("db".equals(type) || "both".equals(type)) {
                requestService.clearDatabaseForExercise(exerciseId);
                stateService.resetStatus(exerciseId);
                log.info("Database cleared for exercise {}", exerciseId);
            }

            if ("files".equals(type) || "both".equals(type)) {
                clearRepositoryFiles();
                log.info("Repository files cleared");
            }

            return ResponseEntity.ok("Data cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear data for exercise {}", exerciseId, e);
            return ResponseEntity.internalServerError().body("Failed to clear data: " + e.getMessage());
        }
    }

    /**
     * Legacy recompute endpoint for backwards compatibility.
     *
     * @param course   the course identifier
     * @param exercise the exercise identifier
     * @return a response entity
     */
    @PostMapping("/{course}/{exercise}/recompute")
    public ResponseEntity<String> recompute(@PathVariable String course, @PathVariable String exercise) {
        log.info("Recompute requested for course: {}, exercise: {}", course, exercise);
        return ResponseEntity.ok("Recompute triggered");
    }

    private void clearRepositoryFiles() throws IOException {
        // Clear ~/.harmonia/repos
        Path reposDir = Paths.get(System.getProperty("user.home"), ".harmonia", "repos");
        deleteDirectoryContents(reposDir, "~/.harmonia/repos");

        // Clear Projects folder (relative to working directory)
        Path projectsDir = Paths.get("Projects");
        deleteDirectoryContents(projectsDir, "Projects");
    }

    private void deleteDirectoryContents(Path dir, String dirName) throws IOException {
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
                status.getErrorMessage());
    }
}

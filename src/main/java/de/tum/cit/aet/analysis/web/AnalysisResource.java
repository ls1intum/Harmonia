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
     */
    @GetMapping("/{exerciseId}/status")
    public ResponseEntity<AnalysisStatusDTO> getStatus(@PathVariable Long exerciseId) {
        AnalysisStatus status = stateService.getStatus(exerciseId);
        return ResponseEntity.ok(toDTO(status));
    }

    /**
     * Cancel a running analysis.
     */
    @PostMapping("/{exerciseId}/cancel")
    public ResponseEntity<AnalysisStatusDTO> cancelAnalysis(@PathVariable Long exerciseId) {
        log.info("Cancel requested for exercise: {}", exerciseId);
        AnalysisStatus status = stateService.cancelAnalysis(exerciseId);
        return ResponseEntity.ok(toDTO(status));
    }

    /**
     * Clear data for an exercise.
     * 
     * @param type One of: "db", "files", "both"
     */
    @DeleteMapping("/{exerciseId}/clear")
    public ResponseEntity<String> clearData(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "both") String type) {
        log.info("Clear requested for exercise: {}, type: {}", exerciseId, type);

        try {
            if ("db".equals(type) || "both".equals(type)) {
                requestService.clearDatabase();
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
     */
    @PostMapping("/{course}/{exercise}/recompute")
    public ResponseEntity<String> recompute(@PathVariable String course, @PathVariable String exercise) {
        log.info("Recompute requested for course: {}, exercise: {}", course, exercise);
        return ResponseEntity.ok("Recompute triggered");
    }

    private void clearRepositoryFiles() throws IOException {
        Path reposDir = Paths.get(System.getProperty("user.home"), ".harmonia", "repos");
        if (Files.exists(reposDir)) {
            try (Stream<Path> walk = Files.walk(reposDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete {}: {}", path, e.getMessage());
                            }
                        });
            }
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

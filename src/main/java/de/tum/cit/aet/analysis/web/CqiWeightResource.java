package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.dto.cqi.CqiWeightsDTO;
import de.tum.cit.aet.analysis.service.cqi.CqiWeightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for per-exercise CQI weight configuration.
 */
@RestController
@RequestMapping("/api/exercises/{exerciseId}/cqi-weights")
@Slf4j
@RequiredArgsConstructor
public class CqiWeightResource {

    private final CqiWeightService cqiWeightService;

    /**
     * Returns the CQI weights for an exercise, falling back to defaults if none are configured.
     *
     * @param exerciseId the exercise ID
     * @return the current weights
     */
    @GetMapping
    public ResponseEntity<CqiWeightsDTO> getWeights(@PathVariable Long exerciseId) {
        log.info("GET cqi-weights for exerciseId={}", exerciseId);
        return ResponseEntity.ok(cqiWeightService.getWeights(exerciseId));
    }

    /**
     * Saves custom CQI weights for an exercise.
     *
     * @param exerciseId the exercise ID
     * @param request    the weights to save
     * @return the saved weights
     */
    @PutMapping
    public ResponseEntity<?> saveWeights(@PathVariable Long exerciseId, @RequestBody CqiWeightsDTO request) {
        log.info("PUT cqi-weights for exerciseId={}", exerciseId);
        try {
            return ResponseEntity.ok(cqiWeightService.saveWeights(exerciseId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Resets CQI weights for an exercise back to application defaults.
     *
     * @param exerciseId the exercise ID
     * @return the default weights
     */
    @DeleteMapping
    public ResponseEntity<CqiWeightsDTO> resetWeights(@PathVariable Long exerciseId) {
        log.info("DELETE cqi-weights for exerciseId={}", exerciseId);
        return ResponseEntity.ok(cqiWeightService.resetWeights(exerciseId));
    }
}

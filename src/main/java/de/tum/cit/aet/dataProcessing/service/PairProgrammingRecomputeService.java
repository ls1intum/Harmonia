package de.tum.cit.aet.dataProcessing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairProgrammingRecomputeService {

    private final RequestService requestService;

    /**
     * Recomputes pair programming metrics for an exercise asynchronously in the background.
     *
     * @param exerciseId the exercise ID
     */
    @Async("attendanceTaskExecutor")
    public void recomputePairProgrammingForExerciseAsync(Long exerciseId) {
        try {
            int updatedTeams = requestService.recomputePairProgrammingForExercise(exerciseId);
            log.info(
                    "Async recomputed pair programming metrics for {} teams (exercise={})",
                    updatedTeams,
                    exerciseId
            );
        } catch (Exception e) {
            log.warn("Async pair programming recomputation failed for exercise {}: {}", exerciseId, e.getMessage(), e);
        }
    }
}

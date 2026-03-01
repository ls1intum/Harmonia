package de.tum.cit.aet.dataProcessing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairProgrammingRecomputeService {

    private final RequestService requestService;
    private final Map<Long, RecomputeState> statesByExerciseId = new ConcurrentHashMap<>();

    private static final class RecomputeState {
        private boolean running;
        private volatile long requestedRevision;
    }

    /**
     * Recomputes pair programming metrics for an exercise asynchronously in the background.
     *
     * @param exerciseId the exercise ID
     */
    @Async("attendanceTaskExecutor")
    public void recomputePairProgrammingForExerciseAsync(Long exerciseId) {
        RecomputeState state = statesByExerciseId.computeIfAbsent(exerciseId, ignored -> new RecomputeState());

        synchronized (state) {
            state.requestedRevision++;
            if (state.running) {
                log.info("Pair programming recomputation already running for exercise {}, requested restart with revision {}",
                        exerciseId,
                        state.requestedRevision);
                return;
            }
            state.running = true;
        }

        try {
            while (true) {
                final long runRevision = state.requestedRevision;
                log.info("Starting pair programming recomputation run for exercise {} (revision={})", exerciseId, runRevision);

                // Ensure previous pair programming values are replaced by a fresh run.
                requestService.clearPairProgrammingForExercise(exerciseId);
                requestService.persistPairProgrammingStatusForExercise(exerciseId);

                int updatedTeams = requestService.recomputePairProgrammingForExercise(
                        exerciseId,
                        () -> state.requestedRevision == runRevision);
                log.info(
                        "Async recomputed pair programming metrics for {} teams (exercise={}, revision={})",
                        updatedTeams,
                        exerciseId,
                        runRevision
                );

                synchronized (state) {
                    if (state.requestedRevision == runRevision) {
                        state.running = false;
                        statesByExerciseId.remove(exerciseId, state);
                        break;
                    }
                    log.info("Newer pair programming recomputation request detected for exercise {}, restarting (latest revision={})",
                            exerciseId,
                            state.requestedRevision);
                }
            }
        } catch (Exception e) {
            log.warn("Async pair programming recomputation failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            synchronized (state) {
                state.running = false;
                statesByExerciseId.remove(exerciseId, state);
            }
        }
    }

    /**
     * Returns whether a pair programming recomputation is currently running for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return true if a recompute is in progress, false otherwise
     */
    public boolean isRecomputeInProgress(Long exerciseId) {
        return statesByExerciseId.containsKey(exerciseId);
    }
}

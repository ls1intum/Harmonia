package de.tum.cit.aet.pairProgramming.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which exercises are currently having their pair programming metrics recomputed.
 * Used to expose "scores still updating" state to the webapp.
 */
@Component
public class PairProgrammingRecomputeTracker {

    private final Set<Long> exerciseIdsInProgress = ConcurrentHashMap.newKeySet();

    /**
     * Marks the given exercise as currently recomputing pair programming metrics.
     *
     * @param exerciseId the exercise ID
     */
    public void startRecompute(Long exerciseId) {
        exerciseIdsInProgress.add(exerciseId);
    }

    /**
     * Marks the given exercise as no longer recomputing.
     *
     * @param exerciseId the exercise ID
     */
    public void endRecompute(Long exerciseId) {
        exerciseIdsInProgress.remove(exerciseId);
    }

    /**
     * Returns whether pair programming scores are currently being recomputed for the exercise.
     *
     * @param exerciseId the exercise ID
     * @return true if a recomputation is in progress
     */
    public boolean isRecomputing(Long exerciseId) {
        return exerciseIdsInProgress.contains(exerciseId);
    }
}

package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.repository.AnalysisStatusRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing analysis state lifecycle.
 * Provides thread-safe operations for starting, updating, completing, and
 * cancelling analyses.
 */
@Service
@Slf4j
public class AnalysisStateService {

    private final AnalysisStatusRepository statusRepository;

    public AnalysisStateService(AnalysisStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    /**
     * On startup, set any RUNNING states to PAUSED (preserving progress).
     * This handles the case where the server was restarted during an analysis.
     */
    @PostConstruct
    @Transactional
    public void cleanupOrphanedAnalyses() {
        List<AnalysisStatus> runningAnalyses = statusRepository.findByState(AnalysisState.RUNNING);
        if (!runningAnalyses.isEmpty()) {
            log.info("Found {} orphaned RUNNING analyses, setting to PAUSED to preserve progress",
                    runningAnalyses.size());
            for (AnalysisStatus status : runningAnalyses) {
                status.setState(AnalysisState.PAUSED);
                status.setLastUpdatedAt(Instant.now());
                statusRepository.save(status);
                log.info("Paused orphaned analysis for exercise {} (processed: {}/{})",
                        status.getExerciseId(), status.getProcessedTeams(), status.getTotalTeams());
            }
        }
    }

    /**
     * Get the current status for an exercise. Returns IDLE status if none exists.
     *
     * @param exerciseId The ID of the exercise
     * @return The analysis status
     */
    @Transactional(readOnly = true)
    public AnalysisStatus getStatus(Long exerciseId) {
        return statusRepository.findById(exerciseId)
                .orElseGet(() -> {
                    AnalysisStatus status = new AnalysisStatus(exerciseId);
                    return status;
                });
    }

    /**
     * Start an analysis for the given exercise. If paused, resumes from where it left off.
     *
     * @param exerciseId The ID of the exercise
     * @param totalTeams The total number of teams to process (only used if starting fresh)
     * @return The updated analysis status
     * @throws IllegalStateException if analysis is already running
     */
    @Transactional
    public AnalysisStatus startAnalysis(Long exerciseId, int totalTeams) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseGet(() -> new AnalysisStatus(exerciseId));

        if (status.getState() == AnalysisState.RUNNING) {
            throw new IllegalStateException("Analysis is already running for exercise " + exerciseId);
        }

        // If paused, resume instead of starting fresh
        if (status.getState() == AnalysisState.PAUSED) {
            status.setState(AnalysisState.RUNNING);
            status.setLastUpdatedAt(Instant.now());
            log.info("Resuming analysis for exercise {} from {} teams processed",
                    exerciseId, status.getProcessedTeams());
            return statusRepository.save(status);
        }

        // Starting fresh
        status.setState(AnalysisState.RUNNING);
        status.setTotalTeams(totalTeams);
        status.setProcessedTeams(0);
        status.setCurrentTeamName(null);
        status.setCurrentStage(null);
        status.setStartedAt(Instant.now());
        status.setLastUpdatedAt(Instant.now());
        status.setErrorMessage(null);

        log.info("Started analysis for exercise {} with {} teams", exerciseId, totalTeams);
        return statusRepository.save(status);
    }

    /**
     * Update progress for a running analysis.
     *
     * @param exerciseId The ID of the exercise
     * @param teamName   The name of the team currently being processed
     * @param stage      The current stage of processing
     * @param processed  The number of teams processed so far
     * @return The updated analysis status
     * @throws IllegalStateException if analysis is not running
     */
    @Transactional
    public AnalysisStatus updateProgress(Long exerciseId, String teamName, String stage, int processed) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseThrow(() -> new IllegalStateException("No analysis found for exercise " + exerciseId));

        if (status.getState() != AnalysisState.RUNNING) {
            throw new IllegalStateException("Analysis is not running for exercise " + exerciseId);
        }

        status.setCurrentTeamName(teamName);
        status.setCurrentStage(stage);
        status.setProcessedTeams(processed);
        status.setLastUpdatedAt(Instant.now());

        return statusRepository.save(status);
    }

    /**
     * Mark analysis as successfully completed.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     */
    @Transactional
    public AnalysisStatus completeAnalysis(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseThrow(() -> new IllegalStateException("No analysis found for exercise " + exerciseId));

        status.setState(AnalysisState.DONE);
        status.setCurrentTeamName(null);
        status.setCurrentStage(null);
        status.setLastUpdatedAt(Instant.now());

        log.info("Completed analysis for exercise {}", exerciseId);
        return statusRepository.save(status);
    }

    /**
     * Mark analysis as failed with an error message.
     *
     * @param exerciseId   The ID of the exercise
     * @param errorMessage The error message
     * @return The updated analysis status
     */
    @Transactional
    public AnalysisStatus failAnalysis(Long exerciseId, String errorMessage) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseGet(() -> new AnalysisStatus(exerciseId));

        status.setState(AnalysisState.ERROR);
        status.setErrorMessage(errorMessage);
        status.setCurrentTeamName(null);
        status.setCurrentStage(null);
        status.setLastUpdatedAt(Instant.now());

        log.error("Analysis failed for exercise {}: {}", exerciseId, errorMessage);
        return statusRepository.save(status);
    }

    /**
     * Cancel/pause a running analysis. Sets state to PAUSED to preserve progress.
     * Safe to call even if not running.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     */
    @Transactional
public AnalysisStatus cancelAnalysis(Long exerciseId) {
    AnalysisStatus status = statusRepository.findById(exerciseId)
            .orElseGet(() -> new AnalysisStatus(exerciseId));

    // Always set to PAUSED when cancelling (preserves progress)
    if (status.getState() != AnalysisState.PAUSED) {
        status.setState(AnalysisState.PAUSED);
        status.setLastUpdatedAt(Instant.now());
        log.info("Cancelled/paused analysis for exercise {} (preserving progress: {}/{})",
                exerciseId, status.getProcessedTeams(), status.getTotalTeams());
        return statusRepository.save(status);
    }

    return status;
}

    /**
     * Pause a running analysis. Preserves progress so it can be continued later.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     */
    @Transactional
    public AnalysisStatus pauseAnalysis(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId).orElse(null);

        if (status == null) {
            return new AnalysisStatus(exerciseId);
        }

        if (status.getState() == AnalysisState.RUNNING) {
            status.setState(AnalysisState.PAUSED);
            status.setLastUpdatedAt(Instant.now());
            log.info("Paused analysis for exercise {} (processed: {}/{})",
                    exerciseId, status.getProcessedTeams(), status.getTotalTeams());
            return statusRepository.save(status);
        }

        return status;
    }

    /**
     * Resume a paused analysis. Continues from where it left off.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     * @throws IllegalStateException if analysis is not paused
     */
    @Transactional
    public AnalysisStatus resumeAnalysis(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseThrow(() -> new IllegalStateException("No analysis found for exercise " + exerciseId));

        if (status.getState() != AnalysisState.PAUSED) {
            throw new IllegalStateException("Analysis is not paused for exercise " + exerciseId);
        }

        status.setState(AnalysisState.RUNNING);
        status.setLastUpdatedAt(Instant.now());
        log.info("Resumed analysis for exercise {} (processed: {}/{})",
                exerciseId, status.getProcessedTeams(), status.getTotalTeams());
        return statusRepository.save(status);
    }

    /**
     * Reset the status to IDLE, clearing all progress data.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     */
    @Transactional
    public AnalysisStatus resetStatus(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseGet(() -> new AnalysisStatus(exerciseId));

        status.reset();
        log.info("Reset analysis status for exercise {}", exerciseId);
        return statusRepository.save(status);
    }

    /**
     * Check if an analysis is currently running for the given exercise.
     *
     * @param exerciseId The ID of the exercise
     * @return true if analysis is running, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isRunning(Long exerciseId) {
        return statusRepository.findById(exerciseId)
                .map(s -> s.getState() == AnalysisState.RUNNING)
                .orElse(false);
    }
}

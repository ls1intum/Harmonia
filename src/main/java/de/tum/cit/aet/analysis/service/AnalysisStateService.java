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
     * On startup, set any RUNNING states to CANCELLED.
     * This handles the case where the server was restarted during an analysis.
     */
    @PostConstruct
    @Transactional
    public void cleanupOrphanedAnalyses() {
        List<AnalysisStatus> runningAnalyses = statusRepository.findByState(AnalysisState.RUNNING);
        if (!runningAnalyses.isEmpty()) {
            log.info("Found {} orphaned RUNNING analyses, setting to CANCELLED",
                    runningAnalyses.size());
            for (AnalysisStatus status : runningAnalyses) {
                status.setState(AnalysisState.CANCELLED);
                status.setLastUpdatedAt(Instant.now());
                statusRepository.save(status);
                log.info("Cancelled orphaned analysis for exercise {} (processed: {}/{})",
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
     * Start an analysis for the given exercise. Always starts fresh.
     *
     * @param exerciseId The ID of the exercise
     * @param totalTeams The total number of teams to process
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

        // Always start fresh
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

        if ("DONE".equals(stage)) {
            if (teamName != null) {
                // If finishing a team, clear the current team name ONLY if it matches the one
                // we just finished.
                statusRepository.updateProgressAndClearNameIfMatching(exerciseId, teamName, stage, processed);

                if (teamName.equals(status.getCurrentTeamName())) {
                    status.setCurrentTeamName(null);
                }
            } else {
                statusRepository.updateProgress(exerciseId, stage, processed);
            }
        } else {
            // Not DONE (Downloading / Analyzing)
            if (teamName != null) {
                // Set the current team name (overwrite)
                statusRepository.updateProgressAndName(exerciseId, teamName, stage, processed);
                status.setCurrentTeamName(teamName);
            } else {
                statusRepository.updateProgress(exerciseId, stage, processed);
            }
        }

        status.setCurrentStage(stage);
        status.setProcessedTeams(processed);
        return status;
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
     * Cancel a running analysis. Sets state to CANCELLED.
     * Safe to call even if not running.
     *
     * @param exerciseId The ID of the exercise
     * @return The updated analysis status
     */
    @Transactional
    public AnalysisStatus cancelAnalysis(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId).orElse(null);

        if (status == null) {
            return new AnalysisStatus(exerciseId);
        }

        if (status.getState() == AnalysisState.RUNNING) {
            status.setState(AnalysisState.CANCELLED);
            status.setLastUpdatedAt(Instant.now());
            log.info("Cancelled analysis for exercise {} (processed: {}/{})",
                    exerciseId, status.getProcessedTeams(), status.getTotalTeams());
            return statusRepository.save(status);
        }

        return status;
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

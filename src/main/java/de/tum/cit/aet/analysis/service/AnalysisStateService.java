package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.repository.AnalysisStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
     * Get the current status for an exercise. Returns IDLE status if none exists.
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
     * Start an analysis for the given exercise.
     * 
     * @throws IllegalStateException if analysis is already running
     */
    @Transactional
    public AnalysisStatus startAnalysis(Long exerciseId, int totalTeams) {
        AnalysisStatus status = statusRepository.findById(exerciseId)
                .orElseGet(() -> new AnalysisStatus(exerciseId));

        if (status.getState() == AnalysisState.RUNNING) {
            throw new IllegalStateException("Analysis is already running for exercise " + exerciseId);
        }

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
     * Cancel a running analysis. Safe to call even if not running.
     */
    @Transactional
    public AnalysisStatus cancelAnalysis(Long exerciseId) {
        AnalysisStatus status = statusRepository.findById(exerciseId).orElse(null);

        if (status == null) {
            return new AnalysisStatus(exerciseId);
        }

        if (status.getState() == AnalysisState.RUNNING) {
            status.reset();
            log.info("Cancelled analysis for exercise {}", exerciseId);
            return statusRepository.save(status);
        }

        return status;
    }

    /**
     * Reset the status to IDLE, clearing all progress data.
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
     */
    @Transactional(readOnly = true)
    public boolean isRunning(Long exerciseId) {
        return statusRepository.findById(exerciseId)
                .map(s -> s.getState() == AnalysisState.RUNNING)
                .orElse(false);
    }
}

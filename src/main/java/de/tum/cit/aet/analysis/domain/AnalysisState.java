package de.tum.cit.aet.analysis.domain;

/**
 * Possible states for an exercise analysis.
 */
public enum AnalysisState {
    IDLE, // Not started or reset
    RUNNING, // Currently analyzing
    DONE, // Completed successfully
    ERROR, // Failed with error
    PAUSED
}

package de.tum.cit.aet.repositoryProcessing.domain;

/**
 * Represents the progression of a team's analysis workflow.
 */
public enum AnalysisStatus {
    PENDING,
    DOWNLOADING,
    GIT_ANALYZING,
    GIT_DONE,
    AI_ANALYZING,
    ANALYZING,  // Legacy - kept for backwards compatibility
    DONE,
    ERROR,
    CANCELLED
}

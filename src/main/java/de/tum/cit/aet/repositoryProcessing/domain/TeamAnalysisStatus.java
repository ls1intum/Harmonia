package de.tum.cit.aet.repositoryProcessing.domain;

/**
 * Represents the progression of a team's analysis workflow.
 */
public enum TeamAnalysisStatus {
    PENDING,
    DOWNLOADING,
    GIT_ANALYZING,
    GIT_DONE,
    AI_ANALYZING,
    DONE,
    ERROR,
    CANCELLED
}

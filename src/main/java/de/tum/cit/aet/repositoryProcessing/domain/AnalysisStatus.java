package de.tum.cit.aet.repositoryProcessing.domain;

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

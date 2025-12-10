package de.tum.cit.aet.ai.dto;

/**
 * Anomaly flags for collaboration pattern detection.
 */
public enum AnomalyFlag {
    /** More than 50% of commits in the last 20% of the assignment period. */
    LATE_DUMP,
    /** One person has more than 70% of all commits. */
    SOLO_DEVELOPMENT,
    /** Gap of more than 50% of the assignment period with no commits. */
    INACTIVE_PERIOD,
    /** Commits clustered in short bursts rather than spread out. */
    UNEVEN_DISTRIBUTION
}

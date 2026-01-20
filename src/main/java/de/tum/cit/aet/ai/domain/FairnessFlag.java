package de.tum.cit.aet.ai.domain;

/**
 * Flags indicating potential fairness issues that require manual review.
 */
public enum FairnessFlag {
    /**
     * One team member has significantly more contribution than others.
     * Triggered when one person has >70% of total effort.
     */
    UNEVEN_DISTRIBUTION,

    /**
     * Most work was done close to the deadline.
     * Triggered when >50% of effort is in the last 20% of the project period.
     */
    LATE_WORK_CONCENTRATION,

    /**
     * One team member did almost all the work.
     * Triggered when one person has >85% of total effort.
     */
    SOLO_CONTRIBUTOR,

    /**
     * Significant portion of work appears to be trivial or auto-generated.
     * Triggered when >40% of commits are classified as TRIVIAL.
     */
    HIGH_TRIVIAL_RATIO,

    /**
     * LLM confidence was low for many ratings.
     * Triggered when >30% of ratings have confidence <0.7.
     */
    LOW_CONFIDENCE_RATINGS,

    /**
     * Unable to analyze due to technical issues.
     */
    ANALYSIS_ERROR
}

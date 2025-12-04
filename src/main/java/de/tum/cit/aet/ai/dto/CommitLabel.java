package de.tum.cit.aet.ai.dto;

/**
 * Labels for commit classification.
 */
public enum CommitLabel {
    /** New functionality or significant enhancement. */
    FEATURE,
    /** Fixes a bug or error. */
    BUG_FIX,
    /** Adds or modifies tests only. */
    TEST,
    /** Code restructuring without behavior change. */
    REFACTOR,
    /** Formatting, comments, docs, whitespace, typos. */
    TRIVIAL
}

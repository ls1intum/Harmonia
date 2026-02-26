package de.tum.cit.aet.analysis.dto.cqi;

/**
 * Reasons why a commit was filtered.
 */
public enum FilterReason {
    EMPTY,
    MERGE_COMMIT,
    REVERT_COMMIT,
    RENAME_ONLY,
    FORMAT_ONLY,
    MASS_REFORMAT,
    GENERATED_FILES_ONLY,
    TRIVIAL_MESSAGE,
    SMALL_TRIVIAL_COMMIT
}

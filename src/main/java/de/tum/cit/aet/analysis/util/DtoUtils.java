package de.tum.cit.aet.analysis.util;

/**
 * Utility methods for DTOs in the analysis package.
 */
public final class DtoUtils {

    private DtoUtils() {
        // Utility class
    }

    /**
     * Calculate total lines changed from lines added and deleted.
     *
     * @param linesAdded   number of lines added
     * @param linesDeleted number of lines deleted
     * @return sum of lines added and deleted
     */
    public static int calculateLinesChanged(int linesAdded, int linesDeleted) {
        return linesAdded + linesDeleted;
    }
}

package de.tum.cit.aet.util;

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

    /**
     * Calculates a weighted effort score.
     * Formula: effortScore * (0.5 + 0.3*complexity + 0.2*novelty) / 10
     * Normalizes to roughly match raw effort score but considers quality.
     *
     * @param effortScore raw effort score (1-10)
     * @param complexity  technical complexity (1-10)
     * @param novelty     originality of the work (1-10)
     * @return weighted effort score
     */
    public static double calculateWeightedEffort(double effortScore, double complexity, double novelty) {
        double qualityMultiplier = 0.5 + (0.3 * complexity / 10.0) + (0.2 * novelty / 10.0);
        return effortScore * qualityMultiplier;
    }
}

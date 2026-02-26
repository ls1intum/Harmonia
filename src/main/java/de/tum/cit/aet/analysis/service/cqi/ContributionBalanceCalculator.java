package de.tum.cit.aet.analysis.service.cqi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ContributionBalanceCalculator {

    /**
     * Calculates contribution balance score (0-100) based on commit distribution.
     * Formula: 100 * (1 - stdev(commit_frequency) / max_stdev)
     * Applies a 10% reduction if any single member contributes > 70%.
     *
     * @param commitCounts Map of contributor names to their commit counts
     * @return Contribution balance score between 0 and 100
     */
    public double calculate(Map<String, Integer> commitCounts) {
        // 1) Validate input
        if (commitCounts == null || commitCounts.isEmpty() || commitCounts.size() == 1) {
            return 0.0;
        }

        int totalCommits = commitCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (totalCommits == 0) {
            return 0.0;
        }

        // 2) Compute balance via standard deviation
        double[] frequencies = commitCounts.values().stream()
                .mapToDouble(Integer::doubleValue)
                .toArray();

        double stdev = calculateStandardDeviation(frequencies);
        double maxStdev = calculateMaxStandardDeviation(frequencies.length);
        if (maxStdev == 0) {
            return 100.0;
        }

        double balanceScore = 100.0 * (1.0 - stdev / maxStdev);

        // 3) Apply 10% reduction if any member contributes > 70%
        if (hasOverContributor(commitCounts)) {
            balanceScore *= 0.9;
        }

        return Math.max(0.0, Math.min(100.0, balanceScore));
    }

    private double calculateStandardDeviation(double[] values) {
        if (values.length <= 1) {
            return 0.0;
        }

        double mean = calculateMean(values);
        double sumSquaredDiffs = 0.0;

        for (double value : values) {
            sumSquaredDiffs += Math.pow(value - mean, 2);
        }

        return Math.sqrt(sumSquaredDiffs / values.length);
    }

    private double calculateMean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private double calculateMaxStandardDeviation(int teamSize) {
        // Maximum standard deviation occurs when one person has all commits, others have 0
        // For n people: one has n commits, (n-1) have 0 commits
        // Mean = 1, stdev = sqrt((n-1)^2 + (n-1)*1^2) / n) = sqrt(n-1)
        return Math.sqrt(teamSize - 1);
    }

    private boolean hasOverContributor(Map<String, Integer> commitCounts) {
        int totalCommits = commitCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalCommits == 0) {
            return false;
        }

        return commitCounts.values().stream()
                .anyMatch(count -> count > totalCommits * 0.7);  // Check if ANY person > 70%
    }
}

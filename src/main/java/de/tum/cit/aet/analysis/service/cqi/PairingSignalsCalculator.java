package de.tum.cit.aet.analysis.service.cqi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class PairingSignalsCalculator {

    private static final double ALTERNATION_THRESHOLD = 0.30;
    private static final double CO_EDITING_THRESHOLD = 0.15;
    private static final int CO_EDIT_TIME_WINDOW_MINUTES = 10;

    /**
     * Calculates pairing signals score (0-100) based on collaboration patterns
     * Formula: 100 * (alternationRate * coEditingRate)
     * Applies penalty if alternation < 30% or co-editing < 15%
     */
    public double calculate(List<CommitInfo> commits) {
        if (commits == null || commits.isEmpty()) {
            log.warn("No commit data provided for pairing signals calculation");
            return 0.0;
        }

        if (commits.size() < 2) {
            log.info("Insufficient commits for pairing analysis");
            return 0.0;
        }

        double alternationRate = calculateAlternationRate(commits);
        double coEditingRate = calculateCoEditingRate(commits);

        log.info("Alternation rate: {}, Co-editing rate: {}", alternationRate, coEditingRate);

        double pairingScore = 100.0 * alternationRate * coEditingRate;

        // Apply threshold penalties
        if (alternationRate < ALTERNATION_THRESHOLD || coEditingRate < CO_EDITING_THRESHOLD) {
            log.info("Pairing thresholds not met (alternation: {}, co-editing: {}), applying penalty", 
                    alternationRate, coEditingRate);
            pairingScore *= 0.5; // 50% penalty for poor collaboration
        }

        return Math.max(0.0, Math.min(100.0, pairingScore));
    }

    /**
     * Calculates alternation rate: fraction of commits where team members alternate on same files
     */
    private double calculateAlternationRate(List<CommitInfo> commits) {
        if (commits.size() < 2) return 0.0;

        int alternatingCommits = 0;
        int totalEligibleCommits = 0;

        for (int i = 1; i < commits.size(); i++) {
            CommitInfo current = commits.get(i);
            CommitInfo previous = commits.get(i - 1);

            // Check if commits touch same files
            Set<String> commonFiles = getCommonFiles(current.getModifiedFiles(), previous.getModifiedFiles());
            
            if (!commonFiles.isEmpty()) {
                totalEligibleCommits++;
                
                // Check if different authors worked on same files
                if (!current.getAuthor().equals(previous.getAuthor())) {
                    alternatingCommits++;
                }
            }
        }

        return totalEligibleCommits > 0 ? (double) alternatingCommits / totalEligibleCommits : 0.0;
    }

    /**
     * Calculates co-editing rate: fraction of commits with multiple authors editing same file within time window
     */
    private double calculateCoEditingRate(List<CommitInfo> commits) {
        if (commits.size() < 2) return 0.0;

        int coEditingCommits = 0;
        int totalCommits = commits.size();

        for (int i = 0; i < commits.size(); i++) {
            CommitInfo current = commits.get(i);
            
            // Look for commits within time window
            for (int j = i + 1; j < commits.size(); j++) {
                CommitInfo other = commits.get(j);
                
                // Check if within time window
                if (isWithinTimeWindow(current.getTimestamp(), other.getTimestamp())) {
                    // Check if different authors editing same files
                    if (!current.getAuthor().equals(other.getAuthor()) && 
                        hasCommonFiles(current.getModifiedFiles(), other.getModifiedFiles())) {
                        coEditingCommits++;
                        break; // Count each commit only once
                    }
                } else {
                    break; // Commits are sorted by time, no need to check further
                }
            }
        }

        return totalCommits > 0 ? (double) coEditingCommits / totalCommits : 0.0;
    }

    private Set<String> getCommonFiles(Set<String> files1, Set<String> files2) {
        return files1.stream()
                .filter(files2::contains)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasCommonFiles(Set<String> files1, Set<String> files2) {
        return files1.stream().anyMatch(files2::contains);
    }

    private boolean isWithinTimeWindow(LocalDateTime time1, LocalDateTime time2) {
        long minutesDiff = Math.abs(java.time.Duration.between(time1, time2).toMinutes());
        return minutesDiff <= CO_EDIT_TIME_WINDOW_MINUTES;
    }

    /**
     * Data class representing commit information needed for pairing analysis
     */
    public static class CommitInfo {
        private final String author;
        private final LocalDateTime timestamp;
        private final Set<String> modifiedFiles;

        public CommitInfo(String author, LocalDateTime timestamp, Set<String> modifiedFiles) {
            this.author = author;
            this.timestamp = timestamp;
            this.modifiedFiles = modifiedFiles;
        }

        public String getAuthor() { return author; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Set<String> getModifiedFiles() { return modifiedFiles; }
    }
}
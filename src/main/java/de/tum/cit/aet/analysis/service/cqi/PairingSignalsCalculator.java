package de.tum.cit.aet.analysis.service.cqi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PairingSignalsCalculator {

    private static final double ALTERNATION_THRESHOLD = 0.30;
    private static final double CO_EDITING_THRESHOLD = 0.15;
    @SuppressWarnings("unused") // Reserved for future co-authoring threshold logic
    private static final double CO_AUTHORING_THRESHOLD = 0.10; // At least 10% co-authored commits
    private static final double ALTERNATION_WEIGHT = 0.7; // Alternation contributes 70%
    private static final double CO_EDITING_WEIGHT = 0.2;  // Co-editing contributes 20%
    private static final double CO_AUTHORING_WEIGHT = 0.1; // Co-authoring contributes 10%
    
    private final int coEditTimeWindowMinutes;

    public PairingSignalsCalculator() {
        // Allow override via environment variable, default to 10 minutes
        String envWindow = System.getenv("PAIRING_TIME_WINDOW_MINUTES");
        this.coEditTimeWindowMinutes = envWindow != null ? Integer.parseInt(envWindow) : 10;
        log.info("Pairing signals time window set to {} minutes", coEditTimeWindowMinutes);
    }

    /**
     * Calculates pairing signals score (0-100) based on collaboration patterns
     * Formula: 100 * (alternationRate * 0.7 + coEditingRate * 0.2 + coAuthoringRate * 0.1)
     * Uses weighted average of three signals for better balance
     * Applies penalty if any signal is significantly below threshold
     *
     * @param commits List of commit information for analysis
     * @return Pairing signals score between 0 and 100
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
        double coAuthoringRate = calculateCoAuthoringRate(commits);

        log.info("Pairing signals - Alternation: {}, Co-editing: {}, Co-authoring: {}", 
                alternationRate, coEditingRate, coAuthoringRate);

        // Use weighted average instead of multiplication for more balanced scoring
        double pairingScore = 100.0 * (
                alternationRate * ALTERNATION_WEIGHT + 
                coEditingRate * CO_EDITING_WEIGHT + 
                coAuthoringRate * CO_AUTHORING_WEIGHT
        );

        // Apply threshold penalties - more lenient than before
        int belowThreshold = 0;
        if (alternationRate < ALTERNATION_THRESHOLD) {
            log.info("Alternation rate below threshold ({} < {})", alternationRate, ALTERNATION_THRESHOLD);
            belowThreshold++;
        }
        if (coEditingRate < CO_EDITING_THRESHOLD) {
            log.info("Co-editing rate below threshold ({} < {})", coEditingRate, CO_EDITING_THRESHOLD);
            belowThreshold++;
        }
        
        // If all three signals are weak, apply penalty
        if (belowThreshold >= 2) {
            log.info("Multiple weak pairing signals detected, applying 20% penalty");
            pairingScore *= 0.8; // 20% penalty (more lenient than previous 50%)
        }

        return Math.max(0.0, Math.min(100.0, pairingScore));
    }

    /**
     * Calculates alternation rate: fraction of commits where different team members are actively committing
     * This detects continuous collaboration between team members, not just same-file editing
     */
    private double calculateAlternationRate(List<CommitInfo> commits) {
        if (commits.size() < 2) {
            return 0.0;
        }

        Set<String> uniqueAuthors = commits.stream()
                .map(CommitInfo::getAuthor)
                .collect(java.util.stream.Collectors.toSet());

        if (uniqueAuthors.size() < 2) {
            log.info("Only {} unique author(s), no alternation possible", uniqueAuthors.size());
            return 0.0; // Need at least 2 different authors for collaboration
        }

        int alternatingCommits = 0;
        String previousAuthor = null;

        for (CommitInfo commit : commits) {
            if (previousAuthor != null && !commit.getAuthor().equals(previousAuthor)) {
                alternatingCommits++;
            }
            previousAuthor = commit.getAuthor();
        }

        double alternationRate = (double) alternatingCommits / (commits.size() - 1);
        log.info("Alternation rate calculated: {}/{} = {}", alternatingCommits, commits.size() - 1, alternationRate);
        return alternationRate;
    }

    /**
     * Calculates co-editing rate: fraction of commits where different authors are collaborating within time window
     * Detects when team members are actively working together by committing in close temporal proximity
     */
    private double calculateCoEditingRate(List<CommitInfo> commits) {
        if (commits.size() < 2) {
            return 0.0;
        }

        int coEditingCommits = 0;
        int totalCommits = commits.size();

        for (int i = 0; i < commits.size(); i++) {
            CommitInfo current = commits.get(i);
            boolean foundCollaborator = false;

            // Look for any commits within time window from a different author
            for (int j = i + 1; j < commits.size(); j++) {
                CommitInfo other = commits.get(j);

                // Check if within time window
                if (isWithinTimeWindow(current.getTimestamp(), other.getTimestamp())) {
                    // Different author within time window = co-editing/collaboration
                    if (!current.getAuthor().equals(other.getAuthor())) {
                        foundCollaborator = true;
                        break;
                    }
                } else {
                    break; // Commits are sorted by time, no need to check further
                }
            }

            if (foundCollaborator) {
                coEditingCommits++;
            }
        }

        double coEditingRate = totalCommits > 0 ? (double) coEditingCommits / totalCommits : 0.0;
        log.info("Co-editing rate calculated: {}/{} = {}", coEditingCommits, totalCommits, coEditingRate);
        return coEditingRate;
    }

    /**
     * Calculates co-authoring rate: fraction of commits explicitly marked with "Co-authored-by:" footer
     * This is a direct indicator of pair programming when properly tracked in commit messages
     * Detects GitHub/GitLab style co-authored commits
     */
    private double calculateCoAuthoringRate(List<CommitInfo> commits) {
        if (commits.isEmpty()) {
            return 0.0;
        }

        int coAuthoredCommits = 0;

        for (CommitInfo commit : commits) {
            // Check if commit has co-authored-by footer (stored in reasoning field if available)
            // Note: Current CommitInfo doesn't store full commit message, so we check for pattern
            // In a real scenario, pass full commit message to CommitInfo
            if (hasCoAuthoredFooter(commit)) {
                coAuthoredCommits++;
            }
        }

        double coAuthoringRate = (double) coAuthoredCommits / commits.size();
        log.info("Co-authoring rate calculated: {}/{} = {}", coAuthoredCommits, commits.size(), coAuthoringRate);
        return coAuthoringRate;
    }

    /**
     * Check if a commit has co-authored-by footer
     * In the current architecture, we don't have direct access to commit message body
     * This method should be enhanced when CommitInfo includes full commit messages
     * 
     * For now, returns false but structure is ready for enhancement
     */
    @SuppressWarnings("unused") // Parameter used for future implementation
    private boolean hasCoAuthoredFooter(CommitInfo commit) {
        // TODO: Enhance CommitInfo to include full commit message body
        // Pattern to detect: "Co-authored-by: Name <email>"
        // This would require passing commit message to CommitInfo
        return false;
    }

    private boolean isWithinTimeWindow(LocalDateTime time1, LocalDateTime time2) {
        long minutesDiff = Math.abs(java.time.Duration.between(time1, time2).toMinutes());
        return minutesDiff <= coEditTimeWindowMinutes;
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

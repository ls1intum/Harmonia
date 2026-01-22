package de.tum.cit.aet.analysis.service.cqi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PairingSignalsCalculator {

    private static final double ALTERNATION_THRESHOLD = 0.30;
    private static final double CO_EDITING_THRESHOLD = 0.15;
    private static final double ALTERNATION_WEIGHT = 0.78; // Alternation contributes ~78%
    private static final double CO_EDITING_WEIGHT = 0.22;  // Co-editing contributes ~22%
    
    private final TeamScheduleService teamScheduleService;

    public PairingSignalsCalculator(TeamScheduleService teamScheduleService) {
        this.teamScheduleService = teamScheduleService;
        log.info("PairingSignalsCalculator initialized with TeamScheduleService");
    }

    /**
     * Calculates pairing signals score (0-100) based on collaboration patterns
     * Formula: 100 * (alternationRate * 0.78 + coEditingRate * 0.22)
     * Uses weighted average of two signals: author alternation and co-editing on class days
     * Applies penalty if both signals are significantly below threshold
     *
     * @param commits List of commit information for analysis
     * @param teamName Optional team name to check class schedule (can be null)
     * @return Pairing signals score between 0 and 100
     */
    public double calculate(List<CommitInfo> commits, String teamName) {
        if (commits == null || commits.isEmpty()) {
            log.warn("No commit data provided for pairing signals calculation");
            return 0.0;
        }

        if (commits.size() < 2) {
            log.info("Insufficient commits for pairing analysis");
            return 0.0;
        }

        double alternationRate = calculateAlternationRate(commits);
        double coEditingRate = calculateCoEditingRate(commits, teamName);

        log.info("Pairing signals - Alternation: {}, Co-editing: {}", 
                alternationRate, coEditingRate);

        // Use weighted average of two signals for balanced scoring
        double pairingScore = 100.0 * (
                alternationRate * ALTERNATION_WEIGHT + 
                coEditingRate * CO_EDITING_WEIGHT
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
        
        // If both signals are weak, apply penalty
        if (belowThreshold >= 2) {
            log.info("Both pairing signals weak, applying 20% penalty");
            pairingScore *= 0.8; // 20% penalty
        }

        return Math.max(0.0, Math.min(100.0, pairingScore));
    }

    /**
     * Overloaded method for backward compatibility (when team name is not available)
     */
    public double calculate(List<CommitInfo> commits) {
        return calculate(commits, null);
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
     * Calculates co-editing rate: fraction of commits where different authors are collaborating on class dates
     * Instead of a time window, this checks if commits happen on the team's scheduled class day
     * When schedule data is available, commits on the same class day indicate pair programming
     * Falls back to checking same-day commits if schedule is not available
     *
     * @param commits List of commits to analyze
     * @param teamName Team name to look up class schedule
     * @return Co-editing rate (0.0 to 1.0)
     */
    private double calculateCoEditingRate(List<CommitInfo> commits, String teamName) {
        if (commits.isEmpty()) {
            return 0.0;
        }

        int coEditingCommits = 0;
        int totalCommits = commits.size();

        // Try to get team's class dates if team name provided
        Set<LocalDate> classDateSet = teamName != null ? 
                teamScheduleService.getClassDates(teamName) : 
                Set.of();

        log.debug("Team {} has {} scheduled class dates", teamName, classDateSet.size());

        for (int i = 0; i < commits.size(); i++) {
            CommitInfo current = commits.get(i);
            boolean foundCollaborator = false;

            // Look for commits from different authors on the same class day or same calendar day
            for (int j = i + 1; j < commits.size(); j++) {
                CommitInfo other = commits.get(j);

                if (!current.getAuthor().equals(other.getAuthor())) {
                    // Check if on same class date (if available)
                    LocalDate currentDate = current.getTimestamp().toLocalDate();
                    LocalDate otherDate = other.getTimestamp().toLocalDate();

                    if (!classDateSet.isEmpty()) {
                        // Use class schedule if available
                        boolean isCollaborationDay = classDateSet.contains(currentDate) && 
                                           classDateSet.contains(otherDate) &&
                                           currentDate.equals(otherDate);
                        if (isCollaborationDay) {
                            log.debug("Checking class date: {} in schedule: true", currentDate);
                            foundCollaborator = true;
                            break;
                        }
                    } else {
                        // Fallback: same calendar day if no schedule
                        if (currentDate.equals(otherDate)) {
                            log.debug("No class schedule available, using same-day collaboration check: true");
                            foundCollaborator = true;
                            break;
                        }
                    }
                }
            }

            if (foundCollaborator) {
                coEditingCommits++;
            }
        }

        double coEditingRate = (double) coEditingCommits / totalCommits;
        log.info("Co-editing rate calculated: {}/{} = {} (team: {})", 
                coEditingCommits, totalCommits, coEditingRate, teamName);
        return coEditingRate;
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

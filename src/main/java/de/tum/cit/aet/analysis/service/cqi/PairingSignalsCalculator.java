package de.tum.cit.aet.analysis.service.cqi;

import java.time.*;
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
    private static final Duration TUTORIAL_DURATION = Duration.ofMinutes(90); // 1.5 hours in minutes

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
     * @param commits  List of commit information for analysis
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

        // Log unique authors for debugging
        java.util.Set<String> uniqueAuthors = commits.stream()
                .map(CommitInfo::getAuthor)
                .collect(java.util.stream.Collectors.toSet());
        log.info("Pairing calculation for team '{}': {} commits, {} unique authors: {}",
                teamName, commits.size(), uniqueAuthors.size(), uniqueAuthors);

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
     * Calculates co-editing rate: fraction of commits where different authors are collaborating
     * Uses the tutorial time slots where both students attended the session to detect collaboration
     * @param commits  List of commits to analyze
     * @param teamName Team name to look up class schedule
     * @return Co-editing rate (0.0 to 1.0)
     */
    private double calculateCoEditingRate(List<CommitInfo> commits, String teamName) {
        if (commits.isEmpty()) {
            return 0.0;
        }

        int coEditingCommits = 0;
        int totalCommits = commits.size();

        // Try to get team's paired session dates (when both attended)
        Set<OffsetDateTime> pairedSessionSet = teamName != null ?
                teamScheduleService.getPairedSessions(teamName) :
                Set.of();

        log.debug("Team {} has {} scheduled class dates", teamName, pairedSessionSet.size());

        if (pairedSessionSet.isEmpty()) {
            log.info("No paired sessions for team {}, co-editing rate set to 0", teamName);
            return 0.0;
        }

        for (int i = 0; i < totalCommits; i++) {
            CommitInfo current = commits.get(i);
            OffsetDateTime commitTime = current.timestamp;

            boolean isCollaboration = pairedSessionSet.stream().anyMatch(pairedSession ->
                    !commitTime.isBefore(pairedSession) &&
                            commitTime.isBefore(pairedSession.plus(TUTORIAL_DURATION))
            );

            if (isCollaboration) {
                coEditingCommits++;
            }
        }

        double coEditingRate = (double) coEditingCommits / totalCommits;
        log.info("Co-editing rate calculated: {}/{} = {} (team: {}, schedule dates: {})",
                coEditingCommits, totalCommits, coEditingRate, teamName, pairedSessionSet.size());
        return coEditingRate;
    }


    /**
     * Data class representing commit information needed for pairing analysis
     */
    public static class CommitInfo {
        private final String author;
        private final OffsetDateTime timestamp;
        private final Set<String> modifiedFiles;

        public CommitInfo(String author, OffsetDateTime timestamp, Set<String> modifiedFiles) {
            this.author = author;
            this.timestamp = timestamp;
            this.modifiedFiles = modifiedFiles;
        }

        public String getAuthor() {
            return author;
        }

        public OffsetDateTime getTimestamp() {
            return timestamp;
        }

        public Set<String> getModifiedFiles() {
            return modifiedFiles;
        }
    }
}

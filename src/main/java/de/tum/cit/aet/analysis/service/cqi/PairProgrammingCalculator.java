package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.core.config.AttendanceConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates pair programming metric to verify collaboration during paired sessions.
 *
 * <p>The metric checks whether both team members committed code on the same calendar dates
 * when they attended pair programming tutorials together. This provides evidence of actual
 * collaboration during scheduled sessions.
 *
 * <p>Formula: Score = (sessions where both committed / total paired sessions) * 100
 *
 * <p>Edge cases:
 * - No paired sessions → null (metric not applicable)
 * - Team size != 2 → null (pair programming requires exactly 2 members)
 * - Empty commits → 0 (no evidence of collaboration)
 * - Only one student committed on any date → lower score
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PairProgrammingCalculator {

    private final AttendanceConfiguration attendanceConfiguration;

    /**
     * Calculate pair programming score based on commits during paired sessions.
     * This overload works with RatedChunk (used during AI analysis phase).
     *
     * @param pairedSessions Set of paired session dates (OffsetDateTime)
     * @param ratedChunks List of rated commit chunks
     * @param teamSize Number of team members
     * @return Score 0-100 if applicable, null if metric not applicable
     */
    public Double calculate(
            Set<OffsetDateTime> pairedSessions,
            List<CQICalculatorService.RatedChunk> ratedChunks,
            int teamSize) {

        // Metric only applies to 2-person teams
        if (teamSize != 2) {
            log.debug("Pair programming metric not applicable: team size is {}, requires 2", teamSize);
            return null;
        }

        // Metric only applies if there are paired sessions
        if (pairedSessions == null || pairedSessions.isEmpty()) {
            log.debug("Pair programming metric not applicable: no paired sessions");
            return null;
        }

        // Handle empty commits
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            log.debug("No commits found for pair programming calculation");
            return 0.0;
        }

        // Extract unique student IDs from commits
        Set<Long> studentIds = new HashSet<>();
        for (CQICalculatorService.RatedChunk rc : ratedChunks) {
            if (rc.chunk().authorId() != null) {
                studentIds.add(rc.chunk().authorId());
            }
        }

        // If 0 or 1 unique students committed, no collaboration possible → score 0
        if (studentIds.size() < 2) {
            log.debug("No pair programming possible: only {} unique student(s) committed", studentIds.size());
            return 0.0;
        }

        // If more than 2 unique students but team size is 2, there's a data inconsistency
        if (studentIds.size() > 2) {
            log.debug("Pair programming metric cannot be applied: {} unique students found but team size is 2", studentIds.size());
            return null;
        }

        Long student1 = studentIds.stream().findFirst().orElse(null);
        Long student2 = studentIds.stream().skip(1).findFirst().orElse(null);


        // Count sessions where both students committed
        int sessionsWithBothCommitted = 0;

        for (OffsetDateTime sessionDateTime : pairedSessions) {
            LocalDate sessionDate = sessionDateTime.toLocalDate();

            // Check if each student has at least one commit on this date
            boolean student1Committed = ratedChunks.stream()
                    .anyMatch(rc -> rc.chunk().authorId().equals(student1)
                            && rc.chunk().timestamp() != null
                            && rc.chunk().timestamp().toLocalDate().equals(sessionDate));

            boolean student2Committed = ratedChunks.stream()
                    .anyMatch(rc -> rc.chunk().authorId().equals(student2)
                            && rc.chunk().timestamp() != null
                            && rc.chunk().timestamp().toLocalDate().equals(sessionDate));

            if (student1Committed && student2Committed) {
                sessionsWithBothCommitted++;
            }
        }

        double score = (double) sessionsWithBothCommitted / pairedSessions.size() * 100.0;

        log.debug("Pair programming score: {}/{}={}",
                sessionsWithBothCommitted, pairedSessions.size(), String.format("%.1f", score));

        return score;
    }

    /**
     * Calculate pair programming score based on raw commit chunks (no LLM ratings needed).
     * This overload works with raw CommitChunkDTO (used during git analysis phase).
     * Scores against ALL sessions in the course, penalizing teams that missed sessions.
     * Score = (paired sessions where both committed) / (total sessions) × 100
     *
     * @param pairedSessions Set of paired session dates (where both attended)
     * @param allSessions Set of all session dates in the course
     * @param chunks List of raw commit chunks
     * @param teamSize Number of team members
     * @return Score 0-100 if applicable, null if metric not applicable
     */
    public Double calculateFromChunks(
            Set<OffsetDateTime> pairedSessions,
            Set<OffsetDateTime> allSessions,
            List<CommitChunkDTO> chunks,
            int teamSize) {

        // Metric only applies to 2-person teams
        if (teamSize != 2) {
            log.debug("Pair programming metric not applicable: team size is {}, requires 2", teamSize);
            return null;
        }

        // Metric only applies if there are paired sessions
        if (pairedSessions == null || pairedSessions.isEmpty()) {
            log.debug("Pair programming metric not applicable: no paired sessions");
            return null;
        }

        // Metric only applies if there are any sessions at all
        if (allSessions == null || allSessions.isEmpty()) {
            log.debug("Pair programming metric not applicable: no sessions found");
            return null;
        }

        // Handle empty commits
        if (chunks == null || chunks.isEmpty()) {
            log.debug("No commits found for pair programming calculation");
            return 0.0;
        }

        // Extract unique student IDs from commits
        Set<Long> studentIds = new HashSet<>();
        for (CommitChunkDTO chunk : chunks) {
            if (chunk.authorId() != null) {
                studentIds.add(chunk.authorId());
            }
        }

        // If 0 or 1 unique students committed, no collaboration possible → score 0
        if (studentIds.size() < 2) {
            log.debug("No pair programming possible: only {} unique student(s) committed", studentIds.size());
            return 0.0;
        }

        // If more than 2 unique students but team size is 2, there's a data inconsistency
        if (studentIds.size() > 2) {
            log.debug("Pair programming metric cannot be applied: {} unique students found but team size is 2", studentIds.size());
            return null;
        }

        Long student1 = studentIds.stream().findFirst().orElse(null);
        Long student2 = studentIds.stream().skip(1).findFirst().orElse(null);

        // Count sessions by commitment type
        int sessionsBothCommitted = 0;    // Both students committed = full credit (1.0)
        int sessionsOnlyOneCommitted = 0; // Only 1 student committed = half credit (0.5)

        log.info("=== Checking commits for paired sessions ===");
        for (OffsetDateTime sessionDateTime : pairedSessions.stream().sorted().toList()) {
            LocalDate sessionDate = sessionDateTime.toLocalDate();

            // Check if each student has at least one commit on this date
            boolean student1Committed = chunks.stream()
                    .anyMatch(c -> c.authorId().equals(student1)
                            && c.timestamp() != null
                            && c.timestamp().toLocalDate().equals(sessionDate));

            boolean student2Committed = chunks.stream()
                    .anyMatch(c -> c.authorId().equals(student2)
                            && c.timestamp() != null
                            && c.timestamp().toLocalDate().equals(sessionDate));

            String status;
            if (student1Committed && student2Committed) {
                status = "✓ BOTH COMMITTED (full credit)";
                sessionsBothCommitted++;
            } else if (student1Committed || student2Committed) {
                status = "⚠ ONLY 1 COMMITTED (50% credit)";
                sessionsOnlyOneCommitted++;
            } else {
                status = "✗ NEITHER COMMITTED";
            }

            log.info("Session {}: {} (S1={}, S2={})",
                    sessionDate, status, student1Committed, student2Committed);
        }

        // Score calculation:
        // Full credit = 1.0 per session (both committed)
        // Half credit = 0.5 per session (only one committed)
        // Minimum requirement is configurable.
        // Score = (full + 0.5*half) / mandatory * 100, capped at 100%
        int minimumRequiredSessions = attendanceConfiguration.getMandatoryProgrammingSessions();
        double totalCredit = sessionsBothCommitted + (0.5 * sessionsOnlyOneCommitted);
        double score = Math.min(100.0, (totalCredit / minimumRequiredSessions) * 100.0);

        log.info("Pair programming result:");
        log.info("  Sessions with both committed: {} (full credit = 1.0 each)", sessionsBothCommitted);
        log.info("  Sessions with only 1 committed: {} (half credit = 0.5 each)", sessionsOnlyOneCommitted);
        log.info("  Total credit: {}", String.format("%.1f", totalCredit));
        log.info("  Minimum requirement: {} sessions", minimumRequiredSessions);
        log.info("Final score: {}%", String.format("%.1f", score));

        return score;
    }
}

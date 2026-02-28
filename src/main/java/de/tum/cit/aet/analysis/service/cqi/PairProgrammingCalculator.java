package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.dto.cqi.CqiRatedChunkDTO;
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
            List<CqiRatedChunkDTO> ratedChunks,
            int teamSize) {

        // 1) Validate preconditions
        if (teamSize != 2) {
            return null;
        }
        if (pairedSessions == null || pairedSessions.isEmpty()) {
            return null;
        }
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            return 0.0;
        }

        // 2) Extract unique student IDs
        Set<Long> studentIds = new HashSet<>();
        for (CqiRatedChunkDTO rc : ratedChunks) {
            if (rc.chunk().authorId() != null) {
                studentIds.add(rc.chunk().authorId());
            }
        }
        if (studentIds.size() < 2) {
            return 0.0;
        }
        if (studentIds.size() > 2) {
            return null;
        }

        Long student1 = studentIds.stream().findFirst().orElse(null);
        Long student2 = studentIds.stream().skip(1).findFirst().orElse(null);

        // 3) Count sessions where both students committed
        int sessionsWithBothCommitted = 0;
        for (OffsetDateTime sessionDateTime : pairedSessions) {
            LocalDate sessionDate = sessionDateTime.toLocalDate();

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

        return (double) sessionsWithBothCommitted / pairedSessions.size() * 100.0;
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

        // 1) Validate preconditions
        if (teamSize != 2) {
            return null;
        }
        if (pairedSessions == null || pairedSessions.isEmpty()) {
            return null;
        }
        if (allSessions == null || allSessions.isEmpty()) {
            return null;
        }
        if (chunks == null || chunks.isEmpty()) {
            return 0.0;
        }

        // 2) Extract unique student IDs
        Set<Long> studentIds = new HashSet<>();
        for (CommitChunkDTO chunk : chunks) {
            if (chunk.authorId() != null) {
                studentIds.add(chunk.authorId());
            }
        }
        if (studentIds.size() < 2) {
            return 0.0;
        }
        if (studentIds.size() > 2) {
            return null;
        }

        Long student1 = studentIds.stream().findFirst().orElse(null);
        Long student2 = studentIds.stream().skip(1).findFirst().orElse(null);

        // 3) Count sessions by commitment type
        int sessionsBothCommitted = 0;
        int sessionsOnlyOneCommitted = 0;

        for (OffsetDateTime sessionDateTime : pairedSessions.stream().sorted().toList()) {
            LocalDate sessionDate = sessionDateTime.toLocalDate();

            boolean student1Committed = chunks.stream()
                    .anyMatch(c -> c.authorId().equals(student1)
                            && c.timestamp() != null
                            && c.timestamp().toLocalDate().equals(sessionDate));

            boolean student2Committed = chunks.stream()
                    .anyMatch(c -> c.authorId().equals(student2)
                            && c.timestamp() != null
                            && c.timestamp().toLocalDate().equals(sessionDate));

            if (student1Committed && student2Committed) {
                sessionsBothCommitted++;
            } else if (student1Committed || student2Committed) {
                sessionsOnlyOneCommitted++;
            }
        }

        // 4) Compute score: full credit (both) + half credit (one) / mandatory sessions
        int minimumRequiredSessions = attendanceConfiguration.getMandatoryProgrammingSessions();
        double totalCredit = sessionsBothCommitted + (0.5 * sessionsOnlyOneCommitted);
        return Math.min(100.0, (totalCredit / minimumRequiredSessions) * 100.0);
    }
}

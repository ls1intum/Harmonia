package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PairProgrammingCalculator.
 * Tests whether both team members committed code on paired session dates.
 */
class PairProgrammingCalculatorTest {

    private PairProgrammingCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PairProgrammingCalculator();
    }

    // ==================== Perfect Pair Programming Tests ====================

    @Test
    void testPerfectPairProgramming() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                // Day 1: Both committed
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(2L, "2024-02-05T15:00:00"),
                // Day 2: Both committed
                createRatedChunk(1L, "2024-02-12T11:00:00"),
                createRatedChunk(2L, "2024-02-12T16:30:00"),
                // Day 3: Both committed
                createRatedChunk(1L, "2024-02-19T09:45:00"),
                createRatedChunk(2L, "2024-02-19T14:15:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null for valid pair programming");
        assertEquals(100.0, score, 0.01, "Perfect pair programming should score 100");
    }

    @Test
    void testPartialPairProgramming() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                // Day 1: Both committed
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(2L, "2024-02-05T15:00:00"),
                // Day 2: Both committed
                createRatedChunk(1L, "2024-02-12T11:00:00"),
                createRatedChunk(2L, "2024-02-12T16:30:00"),
                // Day 3: Only student 1 committed (student 2 didn't attend or contribute)
                createRatedChunk(1L, "2024-02-19T09:45:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(66.67, score, 0.1, "2 of 3 sessions should score ~66.67");
    }

    @Test
    void testNoCollaboration() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                // Only student 1 committed on both dates
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(1L, "2024-02-12T11:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(0.0, score, 0.01, "No collaboration should score 0");
    }

    @Test
    void testMultipleCommitsPerDay() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                // Student 1 commits multiple times
                createRatedChunk(1L, "2024-02-05T10:00:00"),
                createRatedChunk(1L, "2024-02-05T11:00:00"),
                createRatedChunk(1L, "2024-02-05T12:00:00"),
                // Student 2 commits once
                createRatedChunk(2L, "2024-02-05T14:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(100.0, score, 0.01, "Multiple commits same day should count as both present");
    }

    // ==================== Edge Cases ====================

    @Test
    void testNoPairedSessions() {
        Set<OffsetDateTime> pairedSessions = Set.of();

        List<CQICalculatorService.RatedChunk> commits = List.of(
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(2L, "2024-02-05T15:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNull(score, "Should return null when no paired sessions exist");
    }

    @Test
    void testNullPairedSessions() {
        Double score = calculator.calculate(null, List.of(), 2);

        assertNull(score, "Should return null when paired sessions is null");
    }

    @Test
    void testTeamSize3() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(2L, "2024-02-05T15:00:00"),
                createRatedChunk(3L, "2024-02-05T16:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 3);

        assertNull(score, "Should return null for team size != 2");
    }

    @Test
    void testTeamSize1() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                createRatedChunk(1L, "2024-02-05T14:30:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 1);

        assertNull(score, "Should return null for single person team");
    }

    @Test
    void testEmptyCommits() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        Double score = calculator.calculate(pairedSessions, List.of(), 2);

        assertEquals(0.0, score, 0.01, "Should score 0 with no commits");
    }

    @Test
    void testNullCommits() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        Double score = calculator.calculate(pairedSessions, null, 2);

        assertEquals(0.0, score, 0.01, "Should score 0 with null commits");
    }

    @Test
    void testOnlyOneStudentCommitted() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(1L, "2024-02-12T11:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(0.0, score, 0.01, "Only one student committed should score 0");
    }

    @Test
    void testCommitsOnWrongDates() {
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        List<CQICalculatorService.RatedChunk> commits = List.of(
                // Commits on different dates than paired sessions
                createRatedChunk(1L, "2024-03-05T14:30:00"),
                createRatedChunk(2L, "2024-03-05T15:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(0.0, score, 0.01, "No commits on session dates should score 0");
    }

    @Test
    void testTimezoneDifference() {
        // Session at UTC
        Set<OffsetDateTime> pairedSessions = Set.of(
                OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        // Commits are in UTC (should match by date alone)
        List<CQICalculatorService.RatedChunk> commits = List.of(
                createRatedChunk(1L, "2024-02-05T14:30:00"),
                createRatedChunk(2L, "2024-02-05T15:00:00")
        );

        Double score = calculator.calculate(pairedSessions, commits, 2);

        assertNotNull(score, "Score should not be null");
        assertEquals(100.0, score, 0.01, "Date comparison should ignore time/timezone");
    }

    // ==================== Helper Methods ====================

    private CQICalculatorService.RatedChunk createRatedChunk(Long authorId, String timestamp) {
        LocalDateTime dateTime = LocalDateTime.parse(timestamp);
        CommitChunkDTO chunk = CommitChunkDTO.single(
                "commit-" + UUID.randomUUID(),
                authorId,
                "student" + authorId + "@tum.de",
                "Test commit",
                dateTime,
                List.of("test.java"),
                "+1 -0",
                1,
                0
        );

        EffortRatingDTO rating = new EffortRatingDTO(
                5.0,  // effortScore
                0.8,  // complexity
                0.6,  // novelty
                CommitLabel.FEATURE,
                0.9,  // confidence
                "Test effort rating",
                false,
                null
        );

        return new CQICalculatorService.RatedChunk(chunk, rating);
    }
}

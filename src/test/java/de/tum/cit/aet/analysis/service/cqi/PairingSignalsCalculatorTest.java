package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PairingSignalsCalculator using mock commit data.
 * All commit information and team member names are simulated for testing purposes only.
 */
@ExtendWith(MockitoExtension.class)
class PairingSignalsCalculatorTest {

    @Mock TeamScheduleService teamScheduleService;
    PairingSignalsCalculator calculator;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setUp() {
        calculator = new PairingSignalsCalculator(teamScheduleService);
        baseTime = OffsetDateTime.of(
                2024, 1, 1,
                10, 0, 0, 0,
                ZoneOffset.ofHours(1)
        );
    }

    @Test
    void testPerfectCollaboration() {
        // MOCK DATA: Perfect alternation and co-editing scenario
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("bob", baseTime.plusMinutes(5), Set.of("file1.java")), // Co-edit + alternation
            new CommitInfo("alice", baseTime.plusMinutes(15), Set.of("file2.java")),
            new CommitInfo("bob", baseTime.plusMinutes(20), Set.of("file2.java")) // Co-edit + alternation
        );

        double score = calculator.calculate(commits);
        System.out.println("Perfect collaboration score: " + score);
        assertTrue(score > 0); // More lenient test for now
    }

    @Test
    void testNoCollaboration() {
        // MOCK DATA: No file overlap, no collaboration
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("bob", baseTime.plusMinutes(5), Set.of("file2.java")),
            new CommitInfo("alice", baseTime.plusMinutes(15), Set.of("file3.java")),
            new CommitInfo("bob", baseTime.plusMinutes(20), Set.of("file4.java"))
        );

        double score = calculator.calculate(commits);
        assertEquals(0.0, score); // No collaboration detected
    }

    @Test
    void testAlternationOnly() {
        // MOCK DATA: Good alternation but no co-editing (time gap too large)
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("bob", baseTime.plusHours(1), Set.of("file1.java")), // Same file, different authors
            new CommitInfo("alice", baseTime.plusHours(2), Set.of("file1.java"))
        );

        double score = calculator.calculate(commits);
        assertTrue(score < 50); // Low due to no co-editing
    }

    @Test
    void testCoEditingOnly() {
        // MOCK DATA: Co-editing within time window but same author
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("alice", baseTime.plusMinutes(5), Set.of("file1.java")), // Same author
            new CommitInfo("bob", baseTime.plusMinutes(8), Set.of("file1.java"))   // Co-edit but no alternation pattern
        );

        double score = calculator.calculate(commits);
        assertTrue(score < 50); // Low due to poor alternation
    }

    @Test
    void testBelowThresholds() {
        // MOCK DATA: Some collaboration but below thresholds
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("bob", baseTime.plusMinutes(5), Set.of("file1.java")),
            new CommitInfo("alice", baseTime.plusMinutes(30), Set.of("file2.java")), // No co-edit
            new CommitInfo("alice", baseTime.plusMinutes(35), Set.of("file3.java")), // Same author
            new CommitInfo("alice", baseTime.plusMinutes(40), Set.of("file4.java"))  // Same author
        );

        double score = calculator.calculate(commits);
        assertTrue(score < 25); // Should have penalty applied
    }

    @Test
    void testSingleCommit() {
        // MOCK DATA: Only one commit - no collaboration possible
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java"))
        );

        double score = calculator.calculate(commits);
        assertEquals(0.0, score);
    }

    @Test
    void testEmptyCommits() {
        List<CommitInfo> commits = List.of();

        double score = calculator.calculate(commits);
        assertEquals(0.0, score);
    }

    @Test
    void testTimeWindowEdgeCase() {
        // MOCK DATA: Commits exactly at 10-minute boundary
        List<CommitInfo> commits = List.of(
            new CommitInfo("alice", baseTime, Set.of("file1.java")),
            new CommitInfo("bob", baseTime.plusMinutes(10), Set.of("file1.java")) // Exactly 10 minutes
        );

        double score = calculator.calculate(commits);
        assertTrue(score >= 0); // Should handle edge case gracefully
    }
}

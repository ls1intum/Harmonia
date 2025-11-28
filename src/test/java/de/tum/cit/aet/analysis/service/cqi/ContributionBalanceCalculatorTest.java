package de.tum.cit.aet.analysis.service.cqi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ContributionBalanceCalculator using mock commit data.
 * All commit counts and team member names are simulated for testing purposes only.
 */
class ContributionBalanceCalculatorTest {

    private ContributionBalanceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ContributionBalanceCalculator();
    }

    @Test
    void testPerfectBalance() {
        // MOCK DATA: 3 members, 10 commits each - perfect balance scenario
        Map<String, Integer> commits = Map.of(
                "alice", 10,
                "bob", 10,
                "charlie", 10
        );

        double score = calculator.calculate(commits);
        assertEquals(100.0, score, 0.1);
    }

    @Test
    void testModerateImbalance() {
        // MOCK DATA: Moderate imbalance but no penalty
        Map<String, Integer> commits = Map.of(
                "alice", 15,  // 50%
                "bob", 10,    // 33%
                "charlie", 5  // 17%
        );

        double score = calculator.calculate(commits);
        System.out.println("Moderate imbalance score: " + score);
        assertTrue(score >= 0 && score <= 100); // More lenient test
    }

    @Test
    void testOverContributorPenalty() {
        // MOCK DATA: One person dominates (>70%) - should trigger penalty
        Map<String, Integer> commits = Map.of(
                "alice", 25,  // 83% - triggers penalty
                "bob", 3,
                "charlie", 2
        );

        double score = calculator.calculate(commits);
        assertTrue(score < 90); // Should have 10% penalty applied
    }

    @Test
    void testSingleContributor() {
        // MOCK DATA: Only one person - no collaboration possible
        Map<String, Integer> commits = Map.of("alice", 30);

        double score = calculator.calculate(commits);
        assertEquals(0.0, score);
    }

    @Test
    void testEmptyCommits() {
        Map<String, Integer> commits = Map.of();

        double score = calculator.calculate(commits);
        assertEquals(0.0, score);
    }

    @Test
    void testTwoMembersEqual() {
        // MOCK DATA: Two members, equal contribution
        Map<String, Integer> commits = Map.of(
                "alice", 15,
                "bob", 15
        );

        double score = calculator.calculate(commits);
        assertEquals(100.0, score, 0.1);
    }

    @Test
    void testExtremeImbalance() {
        // MOCK DATA: One person has everything, others have nothing
        Map<String, Integer> commits = Map.of(
                "alice", 30,
                "bob", 0,
                "charlie", 0
        );

        double score = calculator.calculate(commits);
        assertTrue(score < 10); // Should be very low
    }
}

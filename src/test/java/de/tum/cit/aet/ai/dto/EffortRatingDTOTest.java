package de.tum.cit.aet.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffortRatingDTO.
 */
class EffortRatingDTOTest {

    @Test
    void testWeightedEffort_highQuality() {
        // High effort (8), high complexity (8), high novelty (9)
        EffortRatingDTO rating = new EffortRatingDTO(
                8.0, 8.0, 9.0, CommitLabel.FEATURE, 0.95, "Complex new feature");

        double weighted = rating.weightedEffort();
        // Formula: 8 * (0.5 + 0.3*8/10 + 0.2*9/10) = 8 * (0.5 + 0.24 + 0.18) = 8 * 0.92
        // = 7.36
        assertEquals(7.36, weighted, 0.01);
    }

    @Test
    void testWeightedEffort_lowQuality() {
        // High effort but low complexity and novelty (copy-paste)
        EffortRatingDTO rating = new EffortRatingDTO(
                7.0, 2.0, 1.0, CommitLabel.TRIVIAL, 0.8, "Copy-paste code");

        double weighted = rating.weightedEffort();
        // Formula: 7 * (0.5 + 0.3*2/10 + 0.2*1/10) = 7 * (0.5 + 0.06 + 0.02) = 7 * 0.58
        // = 4.06
        assertEquals(4.06, weighted, 0.01);
    }

    @Test
    void testWeightedEffort_minimum() {
        // Minimum scores
        EffortRatingDTO rating = new EffortRatingDTO(
                1.0, 1.0, 1.0, CommitLabel.TRIVIAL, 1.0, "Trivial change");

        double weighted = rating.weightedEffort();
        // Formula: 1 * (0.5 + 0.03 + 0.02) = 0.55
        assertEquals(0.55, weighted, 0.01);
    }

    @Test
    void testWeightedEffort_maximum() {
        // Maximum scores
        EffortRatingDTO rating = new EffortRatingDTO(
                10.0, 10.0, 10.0, CommitLabel.FEATURE, 1.0, "Perfect work");

        double weighted = rating.weightedEffort();
        // Formula: 10 * (0.5 + 0.3 + 0.2) = 10 * 1.0 = 10.0
        assertEquals(10.0, weighted, 0.01);
    }

    @Test
    void testTrivialFactory() {
        EffortRatingDTO rating = EffortRatingDTO.trivial("Format only");

        assertEquals(1.0, rating.effortScore());
        assertEquals(1.0, rating.complexity());
        assertEquals(1.0, rating.novelty());
        assertEquals(CommitLabel.TRIVIAL, rating.type());
        assertEquals(1.0, rating.confidence());
        assertEquals("Format only", rating.reasoning());
    }

    @Test
    void testDisabledFactory() {
        EffortRatingDTO rating = EffortRatingDTO.disabled();

        assertEquals(5.0, rating.effortScore());
        assertEquals(5.0, rating.complexity());
        assertEquals(5.0, rating.novelty());
        assertEquals(CommitLabel.TRIVIAL, rating.type());
        assertEquals(0.0, rating.confidence());
        assertEquals("AI disabled", rating.reasoning());
    }

    @Test
    void testAllCommitLabels() {
        for (CommitLabel label : CommitLabel.values()) {
            EffortRatingDTO rating = new EffortRatingDTO(
                    5.0, 5.0, 5.0, label, 0.9, "Test");
            assertEquals(label, rating.type());
        }
    }
}

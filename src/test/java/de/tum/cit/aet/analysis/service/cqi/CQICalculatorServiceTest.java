package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService.RatedChunk;
import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import de.tum.cit.aet.dataProcessing.service.TeamScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CQICalculatorService.
 * Tests the 4-component CQI formula with penalties.
 */
class CQICalculatorServiceTest {

    private CQICalculatorService cqiService;
    private TeamScheduleService teamScheduleService;
    private LocalDateTime projectStart;
    private LocalDateTime projectEnd;
    private String teamName;

    @BeforeEach
    void setUp() {
        teamScheduleService = new TeamScheduleService();
        cqiService = new CQICalculatorService(new CQIConfig(), teamScheduleService, new PairProgrammingCalculator());
        projectStart = LocalDateTime.now().minusDays(30);
        projectEnd = LocalDateTime.now();
        teamName = "team1";

    }

    // ==================== Perfect Balance Tests ====================

    @Test
    void testPerfectBalance_TwoMembers() {
        // Two members, equal effort
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 50, 8.0, CommitLabel.FEATURE, projectStart.plusDays(5)),
                createRatedChunk(1L, 50, 8.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(2L, 50, 8.0, CommitLabel.FEATURE, projectStart.plusDays(15)),
                createRatedChunk(2L, 50, 8.0, CommitLabel.FEATURE, projectStart.plusDays(20))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertTrue(result.cqi() >= 80, "Perfect balance should score >= 80, got: " + result.cqi());
        assertTrue(result.penalties().isEmpty(), "Should have no penalties");
    }

    @Test
    void testPerfectBalance_ThreeMembers() {
        List<RatedChunk> chunks = new ArrayList<>();
        for (long authorId = 1; authorId <= 3; authorId++) {
            for (int i = 0; i < 5; i++) {
                chunks.add(createRatedChunk(authorId, 40, 7.0, CommitLabel.FEATURE,
                        projectStart.plusDays(i * 6)));
            }
        }

        CQIResultDTO result = cqiService.calculate(chunks, 3, projectStart, projectEnd, null, teamName) ;

        assertTrue(result.cqi() >= 75, "Perfect balance should score >= 75, got: " + result.cqi());
    }

    // ==================== Imbalance Tests ====================

    @Test
    void testSevereImbalance() {
        // One person does 80% of the work
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 100, 9.0, CommitLabel.FEATURE, projectStart.plusDays(5)),
                createRatedChunk(1L, 100, 9.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(1L, 100, 9.0, CommitLabel.FEATURE, projectStart.plusDays(15)),
                createRatedChunk(1L, 100, 9.0, CommitLabel.FEATURE, projectStart.plusDays(20)),
                createRatedChunk(2L, 20, 5.0, CommitLabel.TRIVIAL, projectStart.plusDays(25))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertTrue(result.cqi() < 65, "Severe imbalance should score < 65, got: " + result.cqi());
    }

    @Test
    void testSoloDevelopment() {
        // One person does 90% of the work
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 200, 10.0, CommitLabel.FEATURE, projectStart.plusDays(5)),
                createRatedChunk(1L, 200, 10.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(1L, 200, 10.0, CommitLabel.FEATURE, projectStart.plusDays(15)),
                createRatedChunk(2L, 10, 2.0, CommitLabel.TRIVIAL, projectStart.plusDays(20))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertTrue(result.cqi() < 60, "Solo development should score < 60, got: " + result.cqi());
    }

    // ==================== Single Contributor Tests ====================

    @Test
    void testSingleContributor_TeamOfOne() {
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 100, 8.0, CommitLabel.FEATURE, projectStart.plusDays(10))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 1, projectStart, projectEnd, null, teamName);

        assertEquals(0.0, result.cqi(), "Single team member should get CQI = 0");
    }

    @Test
    void testSingleContributor_OnlyOneCommitted() {
        // Team of 2, but only one person committed
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 100, 8.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(1L, 100, 8.0, CommitLabel.FEATURE, projectStart.plusDays(15))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertEquals(0.0, result.cqi(), "Only one contributor should get CQI = 0");
    }

    // ==================== Component Scores Tests ====================

    @Test
    void testComponentScoresCalculation() {
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 100, 8.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(2L, 100, 8.0, CommitLabel.FEATURE, projectStart.plusDays(20))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertNotNull(result.components());
        assertTrue(result.components().effortBalance() >= 0 && result.components().effortBalance() <= 100);
        assertTrue(result.components().locBalance() >= 0 && result.components().locBalance() <= 100);
        assertTrue(result.components().temporalSpread() >= 0 && result.components().temporalSpread() <= 100);
        assertTrue(result.components().ownershipSpread() >= 0 && result.components().ownershipSpread() <= 100);
    }

    // ==================== Fallback Tests ====================

    @Test
    void testFallbackCalculation() {
        List<CommitChunkDTO> chunks = List.of(
                createChunk(1L, 100),
                createChunk(1L, 100),
                createChunk(2L, 100),
                createChunk(2L, 100)
        );

        FilterSummaryDTO summary = FilterSummaryDTO.empty();
        CQIResultDTO result = cqiService.calculateFallback(chunks, 2, summary);

        assertTrue(result.cqi() >= 80, "Equal LoC should score well, got: " + result.cqi());
    }

    @Test
    void testFallbackWithImbalance() {
        List<CommitChunkDTO> chunks = List.of(
                createChunk(1L, 300),
                createChunk(1L, 300),
                createChunk(2L, 50)
        );

        CQIResultDTO result = cqiService.calculateFallback(chunks, 2, null);

        assertTrue(result.cqi() < 60, "Imbalanced LoC should score low, got: " + result.cqi());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyChunks() {
        CQIResultDTO result = cqiService.calculate(List.of(), 2, projectStart, projectEnd, null, teamName);

        assertEquals(0.0, result.cqi());
    }

    @Test
    void testNullChunks() {
        CQIResultDTO result = cqiService.calculate(null, 2, projectStart, projectEnd, null, teamName);

        assertEquals(0.0, result.cqi());
    }

    @Test
    void testCQIBounds() {
        // Ensure CQI is always between 0 and 100
        List<RatedChunk> chunks = List.of(
                createRatedChunk(1L, 1000, 10.0, CommitLabel.FEATURE, projectStart.plusDays(10)),
                createRatedChunk(2L, 1000, 10.0, CommitLabel.FEATURE, projectStart.plusDays(20))
        );

        CQIResultDTO result = cqiService.calculate(chunks, 2, projectStart, projectEnd, null, teamName);

        assertTrue(result.cqi() >= 0 && result.cqi() <= 100,
                "CQI should be 0-100, got: " + result.cqi());
    }

    @Test
    void testGitOnlyComponents_FoundTeamWithoutPairedSessions_IsFoundWithZeroScore() {
        OffsetDateTime session = OffsetDateTime.parse("2025-01-13T10:00:00+01:00");

        TeamAttendanceDTO attendance = new TeamAttendanceDTO(
                Map.of(session, true),
                Map.of(session, false),
                false,
                List.of());
        teamScheduleService.update(new TeamsScheduleDTO(Map.of("Team 01", attendance)));

        List<CommitChunkDTO> chunks = List.of(
                createChunk(1L, 40, session.toLocalDate().atTime(11, 0)),
                createChunk(2L, 45, session.toLocalDate().atTime(12, 0)));

        ComponentScoresDTO components = cqiService.calculateGitOnlyComponents(chunks, 2, null, null, "Team 01");

        assertEquals("FOUND", components.pairProgrammingStatus());
        assertEquals(0.0, components.pairProgramming(), 0.001);
    }

    @Test
    void testGitOnlyComponents_NormalizesTeamNameWithNbspAndWhitespace() {
        OffsetDateTime sessionOne = OffsetDateTime.parse("2025-01-20T10:00:00+01:00");
        OffsetDateTime sessionTwo = OffsetDateTime.parse("2025-01-27T10:00:00+01:00");

        TeamAttendanceDTO attendance = new TeamAttendanceDTO(
                Map.of(sessionOne, true, sessionTwo, true),
                Map.of(sessionOne, true, sessionTwo, true),
                true,
                List.of(sessionOne, sessionTwo));
        teamScheduleService.update(new TeamsScheduleDTO(Map.of("  Team\u00A001  ", attendance)));

        List<CommitChunkDTO> chunks = List.of(
                createChunk(1L, 30, sessionOne.toLocalDate().atTime(10, 15)),
                createChunk(2L, 35, sessionOne.toLocalDate().atTime(10, 45)),
                createChunk(1L, 28, sessionTwo.toLocalDate().atTime(10, 20)),
                createChunk(2L, 32, sessionTwo.toLocalDate().atTime(10, 35)));

        ComponentScoresDTO components = cqiService.calculateGitOnlyComponents(chunks, 2, null, null, "team   01");

        assertEquals("FOUND", components.pairProgrammingStatus());
        assertNotNull(components.pairProgramming());
        assertEquals(100.0, components.pairProgramming(), 0.001);
    }

    // ==================== Helper Methods ====================

    private RatedChunk createRatedChunk(Long authorId, int loc, double effort,
                                         CommitLabel type, LocalDateTime timestamp) {
        return createRatedChunkWithConfidence(authorId, loc, effort, type, 0.9, timestamp);
    }

    private RatedChunk createRatedChunkWithConfidence(Long authorId, int loc, double effort,
                                                       CommitLabel type, double confidence,
                                                       LocalDateTime timestamp) {
        CommitChunkDTO chunk = new CommitChunkDTO(
                "sha-" + System.nanoTime(), authorId, "author" + authorId + "@test.com",
                "Test commit", timestamp,
                List.of("src/File" + authorId + ".java"), "diff",
                loc / 2, loc / 2, 0, 1, false, List.of(),
                null, null, null
        );

        EffortRatingDTO rating = new EffortRatingDTO(
                effort, 7.0, 7.0, type, confidence, "Test reasoning", false, null
        );

        return new RatedChunk(chunk, rating);
    }

    private CommitChunkDTO createChunk(Long authorId, int loc) {
        return createChunk(authorId, loc, LocalDateTime.now());
    }

    private CommitChunkDTO createChunk(Long authorId, int loc, LocalDateTime timestamp) {
        return new CommitChunkDTO(
                "sha-" + System.nanoTime(), authorId, "author" + authorId + "@test.com",
                "Test commit", timestamp,
                List.of("src/File.java"), "diff",
                loc / 2, loc / 2, 0, 1, false, List.of(),
                null, null, null
        );
    }
}

package de.tum.cit.aet;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for the complete analysis pipeline.
 * 
 * This test:
 * 1. Clears existing data from the database
 * 2. Authenticates with Artemis and fetches real team data
 * 3. Triggers analysis for each team (limited by MAX_TEAMS)
 * 4. Verifies that data is correctly persisted to the database
 * 5. Verifies data survives reload (simulating server restart)
 * 
 * Prerequisites:
 * - Database running (local PostgreSQL via Docker)
 * - LM Studio running on localhost:1234
 * - Valid Artemis credentials in src/test/resources/test-credentials.properties
 * 
 * Run with:
 * ./gradlew integrationTest
 */
@SpringBootTest
@ActiveProfiles("local")
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "ARTEMIS_TEST_URL", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndAnalysisIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EndToEndAnalysisIntegrationTest.class);

    @Autowired
    private RequestService requestService;

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AnalyzedChunkRepository analyzedChunkRepository;

    // Test configuration from environment
    private static final String ARTEMIS_URL = System.getenv("ARTEMIS_TEST_URL");
    private static final String USERNAME = System.getenv("ARTEMIS_TEST_USERNAME");
    private static final String PASSWORD = System.getenv("ARTEMIS_TEST_PASSWORD");
    private static final Long EXERCISE_ID = Long.parseLong(
            System.getenv("ARTEMIS_TEST_EXERCISE_ID") != null
                    ? System.getenv("ARTEMIS_TEST_EXERCISE_ID")
                    : "18806");
    private static final int MAX_TEAMS = Integer.parseInt(
            System.getenv("ARTEMIS_TEST_MAX_TEAMS") != null
                    ? System.getenv("ARTEMIS_TEST_MAX_TEAMS")
                    : "15");

    @BeforeAll
    static void printTestConfiguration() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           E2E ANALYSIS INTEGRATION TEST                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║ Configuration:                                               ║");
        System.out.println("║   Artemis URL:  " + padRight(ARTEMIS_URL, 44) + "║");
        System.out.println("║   Username:     " + padRight(USERNAME, 44) + "║");
        System.out.println("║   Exercise ID:  " + padRight(String.valueOf(EXERCISE_ID), 44) + "║");
        System.out.println("║   Max Teams:    " + padRight(String.valueOf(MAX_TEAMS), 44) + "║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static String padRight(String s, int n) {
        if (s == null)
            s = "null";
        return String.format("%-" + n + "s", s.length() > n ? s.substring(0, n - 3) + "..." : s);
    }

    private void logStep(String step, String description) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ STEP: {}", step);
        log.info("│ {}", description);
        log.info("└─────────────────────────────────────────────────────────────");
    }

    private void logSuccess(String message) {
        log.info("✓ SUCCESS: {}", message);
    }

    private void logWarning(String message) {
        log.warn("⚠ WARNING: {}", message);
    }

    private void logResult(String label, Object value) {
        log.info("  → {}: {}", label, value);
    }

    @Test
    @Order(1)
    @DisplayName("Clear database before testing")
    void shouldClearDatabaseBeforeTesting() {
        logStep("1. CLEAR DATABASE", "Removing all existing data to start fresh");

        long chunksBefore = analyzedChunkRepository.count();
        long studentsBefore = studentRepository.count();
        long participationsBefore = teamParticipationRepository.count();

        logResult("Chunks before clear", chunksBefore);
        logResult("Students before clear", studentsBefore);
        logResult("Participations before clear", participationsBefore);

        log.info("Clearing database...");
        requestService.clearDatabase();

        long chunksAfter = analyzedChunkRepository.count();
        long studentsAfter = studentRepository.count();
        long participationsAfter = teamParticipationRepository.count();

        assertEquals(0, participationsAfter, "Participations should be cleared");
        assertEquals(0, studentsAfter, "Students should be cleared");
        assertEquals(0, chunksAfter, "Chunks should be cleared");

        logSuccess("Database cleared successfully");
        logResult("Deleted chunks", chunksBefore);
        logResult("Deleted students", studentsBefore);
        logResult("Deleted participations", participationsBefore);
    }

    @Test
    @Order(2)
    @DisplayName("Authenticate with Artemis and fetch/analyze teams")
    void shouldFetchAndAnalyzeTeamsFromArtemis() throws Exception {
        logStep("2. AUTHENTICATE & ANALYZE", "Connecting to Artemis and analyzing " + MAX_TEAMS + " teams");

        // Validate credentials
        assertNotNull(ARTEMIS_URL, "ARTEMIS_TEST_URL must be set");
        assertNotNull(USERNAME, "ARTEMIS_TEST_USERNAME must be set");
        assertNotNull(PASSWORD, "ARTEMIS_TEST_PASSWORD must be set");

        log.info("Authenticating with Artemis as user '{}'...", USERNAME);
        String jwtToken = artemisClientService.authenticate(ARTEMIS_URL, USERNAME, PASSWORD);
        assertNotNull(jwtToken, "JWT token should not be null after authentication");
        logSuccess("Authentication successful");

        ArtemisCredentials credentials = new ArtemisCredentials(ARTEMIS_URL, jwtToken, USERNAME, PASSWORD);
        assertTrue(credentials.isValid(), "Credentials should be valid");
        assertTrue(credentials.hasGitCredentials(), "Should have Git credentials");

        log.info("");
        log.info("Starting analysis pipeline for exercise {} with max {} teams...", EXERCISE_ID, MAX_TEAMS);
        log.info("This may take several minutes depending on the number of teams and commits...");
        log.info("");

        long startTime = System.currentTimeMillis();

        // Trigger analysis with maxTeams limit
        List<ClientResponseDTO> results = requestService.fetchAnalyzeAndSaveRepositories(
                credentials, EXERCISE_ID, MAX_TEAMS);

        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(results, "Results should not be null");
        assertFalse(results.isEmpty(), "Should have at least one team result");

        logSuccess("Analysis completed");
        logResult("Duration", (duration / 1000) + " seconds");
        logResult("Teams analyzed", results.size());

        // Log each team result
        log.info("");
        log.info("┌────────────────────────────────────────────────────────────────────────────┐");
        log.info("│ TEAM RESULTS                                                               │");
        log.info("├────────────────────────────────────────────────────────────────────────────┤");
        for (ClientResponseDTO result : results) {
            String teamInfo = String.format("│ %-25s | CQI: %6.2f | Suspicious: %-5s | Students: %d | Chunks: %d",
                    truncate(result.teamName(), 25),
                    result.cqi() != null ? result.cqi() : 0.0,
                    result.isSuspicious(),
                    result.students() != null ? result.students().size() : 0,
                    result.analysisHistory() != null ? result.analysisHistory().size() : 0);
            log.info(teamInfo);
        }
        log.info("└────────────────────────────────────────────────────────────────────────────┘");
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "null";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    @Test
    @Order(3)
    @DisplayName("Verify participations are persisted to database")
    void shouldPersistParticipationsToDatabase() {
        logStep("3. VERIFY PARTICIPATIONS", "Checking database for persisted team participations");

        List<TeamParticipation> participations = teamParticipationRepository.findAll();

        assertFalse(participations.isEmpty(), "Should have participations in database");
        logResult("Total participations", participations.size());

        long withCqi = participations.stream()
                .filter(p -> p.getCqi() != null && p.getCqi() > 0)
                .count();
        long suspicious = participations.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsSuspicious()))
                .count();

        logResult("With CQI > 0", withCqi);
        logResult("Flagged as suspicious", suspicious);

        log.info("");
        log.info("Team Details:");
        participations.forEach(p -> log.info("  • {} → CQI: {}, Suspicious: {}",
                p.getName(),
                p.getCqi() != null ? String.format("%.2f", p.getCqi()) : "null",
                p.getIsSuspicious()));

        logSuccess("Participations verified in database");
    }

    @Test
    @Order(4)
    @DisplayName("Verify students are persisted to database")
    void shouldPersistStudentsToDatabase() {
        logStep("4. VERIFY STUDENTS", "Checking database for persisted student data");

        List<Student> students = studentRepository.findAll();

        assertFalse(students.isEmpty(), "Should have students in database");
        logResult("Total students", students.size());

        long withCommits = students.stream()
                .filter(s -> s.getCommitCount() != null && s.getCommitCount() > 0)
                .count();
        int totalCommits = students.stream()
                .mapToInt(s -> s.getCommitCount() != null ? s.getCommitCount() : 0)
                .sum();
        int totalLines = students.stream()
                .mapToInt(s -> s.getLinesChanged() != null ? s.getLinesChanged() : 0)
                .sum();

        logResult("Students with commits", withCommits);
        logResult("Total commits", totalCommits);
        logResult("Total lines changed", totalLines);

        assertTrue(withCommits > 0, "At least some students should have commits");

        logSuccess("Students verified in database");
    }

    @Test
    @Order(5)
    @DisplayName("Verify analyzed chunks are persisted to database")
    void shouldPersistAnalyzedChunksToDatabase() {
        logStep("5. VERIFY CHUNKS", "Checking database for AI-analyzed commit chunks");

        List<AnalyzedChunk> chunks = analyzedChunkRepository.findAll();

        logResult("Total analyzed chunks", chunks.size());

        if (chunks.isEmpty()) {
            logWarning("No analyzed chunks found. AI analysis may have failed or be disabled.");
        } else {
            AnalyzedChunk sample = chunks.get(0);
            assertNotNull(sample.getParticipation(), "Chunk should have participation");
            assertNotNull(sample.getClassification(), "Chunk should have classification");

            // Count by classification
            log.info("");
            log.info("Chunks by classification:");
            chunks.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            c -> c.getClassification() != null ? c.getClassification() : "UNKNOWN",
                            java.util.stream.Collectors.counting()))
                    .forEach((classification, count) -> log.info("  • {}: {}", classification, count));

            // Chunks per team
            log.info("");
            log.info("Chunks per team:");
            teamParticipationRepository.findAll().forEach(p -> {
                long chunkCount = analyzedChunkRepository.findByParticipation(p).size();
                log.info("  • {}: {} chunks", p.getName(), chunkCount);
            });

            logSuccess("Analyzed chunks verified in database");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Verify data survives reload (simulating server restart)")
    void shouldSurviveReload() {
        logStep("6. VERIFY RELOAD", "Simulating server restart by reloading all data from database");

        long participationCount = teamParticipationRepository.count();
        long studentCount = studentRepository.count();
        long chunkCount = analyzedChunkRepository.count();

        logResult("Participations in DB", participationCount);
        logResult("Students in DB", studentCount);
        logResult("Chunks in DB", chunkCount);

        assertTrue(participationCount > 0, "Should have participations before reload test");

        log.info("Reloading data from database (simulating restart)...");
        List<ClientResponseDTO> loadedData = requestService.getAllRepositoryData();

        assertNotNull(loadedData, "Loaded data should not be null");
        assertEquals(participationCount, loadedData.size(), "Should load same number of teams");

        long teamsWithCqi = loadedData.stream()
                .filter(r -> r.cqi() != null && r.cqi() > 0)
                .count();
        long teamsWithHistory = loadedData.stream()
                .filter(r -> r.analysisHistory() != null && !r.analysisHistory().isEmpty())
                .count();

        logResult("Teams loaded", loadedData.size());
        logResult("Teams with CQI after reload", teamsWithCqi);
        logResult("Teams with analysis history", teamsWithHistory);

        if (chunkCount > 0) {
            assertTrue(teamsWithHistory > 0,
                    "If chunks exist in DB, at least some teams should have analysis history");
        }

        logSuccess("Data reload verified - data survives restart!");

        // Final summary
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║           TEST SUMMARY                                       ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ Teams analyzed:         " + padRight(String.valueOf(loadedData.size()), 36) + "║");
        log.info("║ Teams with CQI:         " + padRight(String.valueOf(teamsWithCqi), 36) + "║");
        log.info("║ Teams with AI analysis: " + padRight(String.valueOf(teamsWithHistory), 36) + "║");
        log.info("║ Total students:         " + padRight(String.valueOf(studentCount), 36) + "║");
        log.info("║ Total analyzed chunks:  " + padRight(String.valueOf(chunkCount), 36) + "║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }
}

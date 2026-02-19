package de.tum.cit.aet.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.export.dto.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void export_allSections_containsAllFields() throws IOException {
        LocalDateTime ts = LocalDateTime.of(2025, 1, 15, 10, 30);
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", "t1", "Tutor1", "https://git.example.com/team1",
                        5, "DONE", 0.85,
                        0.9, 0.8, 0.7, 0.6, 0.55, "FOUND", false, 1000L)),
                List.of(new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250)),
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE",
                        0.9, 0.8, 0.7, 0.95, "Good work", "abc123", "[\"init commit\"]",
                        ts, 42, false, 0, 1, false, null, "gpt-4", 100L, 200L, 300L, true)),
                List.of(new CommitExportRow("Team1", "abc123", "alice@test.com")));

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        // Teams - verify all fields
        JsonNode team = root.get("teams").get(0);
        assertEquals("Team1", team.get("teamName").asText());
        assertEquals("t1", team.get("shortName").asText());
        assertEquals("Tutor1", team.get("tutor").asText());
        assertEquals("https://git.example.com/team1", team.get("repositoryUrl").asText());
        assertEquals(5, team.get("submissionCount").asInt());
        assertEquals("DONE", team.get("analysisStatus").asText());
        assertEquals(0.85, team.get("cqi").asDouble(), 0.001);
        assertEquals(0.9, team.get("cqiEffortBalance").asDouble(), 0.001);
        assertEquals(0.8, team.get("cqiLocBalance").asDouble(), 0.001);
        assertEquals(0.7, team.get("cqiTemporalSpread").asDouble(), 0.001);
        assertEquals(0.6, team.get("cqiOwnershipSpread").asDouble(), 0.001);
        assertEquals(0.55, team.get("cqiPairProgramming").asDouble(), 0.001);
        assertEquals("FOUND", team.get("cqiPairProgrammingStatus").asText());
        assertFalse(team.get("isSuspicious").asBoolean());
        assertEquals(1000, team.get("llmTotalTokens").asLong());

        // Students - verify all fields
        JsonNode student = root.get("students").get(0);
        assertEquals("Team1", student.get("teamName").asText());
        assertEquals("Alice", student.get("studentName").asText());
        assertEquals("alice01", student.get("login").asText());
        assertEquals("alice@test.com", student.get("email").asText());
        assertEquals(10, student.get("commitCount").asInt());
        assertEquals(200, student.get("linesAdded").asInt());
        assertEquals(50, student.get("linesDeleted").asInt());
        assertEquals(250, student.get("linesChanged").asInt());

        // Chunks - verify all fields
        JsonNode chunk = root.get("chunks").get(0);
        assertEquals("Team1", chunk.get("teamName").asText());
        assertEquals("Alice", chunk.get("authorName").asText());
        assertEquals("alice@test.com", chunk.get("authorEmail").asText());
        assertEquals("FEATURE", chunk.get("classification").asText());
        assertEquals(0.9, chunk.get("effortScore").asDouble(), 0.001);
        assertEquals(0.8, chunk.get("complexity").asDouble(), 0.001);
        assertEquals(0.7, chunk.get("novelty").asDouble(), 0.001);
        assertEquals(0.95, chunk.get("confidence").asDouble(), 0.001);
        assertEquals("Good work", chunk.get("reasoning").asText());
        assertEquals("abc123", chunk.get("commitShas").asText());
        assertEquals("[\"init commit\"]", chunk.get("commitMessages").asText());
        assertEquals(42, chunk.get("linesChanged").asInt());
        assertFalse(chunk.get("isBundled").asBoolean());
        assertEquals(0, chunk.get("chunkIndex").asInt());
        assertEquals(1, chunk.get("totalChunks").asInt());
        assertFalse(chunk.get("isError").asBoolean());
        assertNull(chunk.get("errorMessage"));
        assertEquals("gpt-4", chunk.get("llmModel").asText());
        assertEquals(100, chunk.get("llmPromptTokens").asLong());
        assertEquals(200, chunk.get("llmCompletionTokens").asLong());
        assertEquals(300, chunk.get("llmTotalTokens").asLong());
        assertTrue(chunk.get("llmUsageAvailable").asBoolean());

        // Commits - verify all fields
        JsonNode commit = root.get("commits").get(0);
        assertEquals("Team1", commit.get("teamName").asText());
        assertEquals("abc123", commit.get("commitHash").asText());
        assertEquals("alice@test.com", commit.get("authorEmail").asText());
    }

    @Test
    void export_nullSections_omitsNullFields() throws IOException {
        ExportData data = new ExportData(null, null, null, null);

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        assertNull(root.get("teams"));
        assertNull(root.get("students"));
        assertNull(root.get("chunks"));
        assertNull(root.get("commits"));
    }

    @Test
    void export_timestamps_serializedAsIsoString() throws IOException {
        LocalDateTime ts = LocalDateTime.of(2025, 3, 20, 14, 45, 30);
        ExportData data = new ExportData(
                null, null,
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE",
                        0.9, 0.8, 0.7, 0.95, "reasoning", "abc123", null,
                        ts, 10, false, 0, 1, false, null, null, null, null, null, null)),
                null);

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        String timestampValue = root.get("chunks").get(0).get("timestamp").asText();
        assertTrue(timestampValue.contains("2025-03-20"), "Expected ISO date string but got: " + timestampValue);
        assertTrue(timestampValue.contains("14:45:30"), "Expected ISO time string but got: " + timestampValue);
    }
}

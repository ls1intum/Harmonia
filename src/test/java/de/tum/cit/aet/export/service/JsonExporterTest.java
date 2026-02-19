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
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                List.of(new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250)),
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE", 0.9, 0.8, 0.7, 0.95,
                        "Good work", "abc123", ts, 42)),
                List.of(new CommitExportRow("Team1", "abc123", "alice@test.com")));

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        // Teams
        assertEquals(1, root.get("teams").size());
        assertEquals("Team1", root.get("teams").get(0).get("teamName").asText());
        assertEquals(0.85, root.get("teams").get(0).get("cqi").asDouble(), 0.001);

        // Students
        assertEquals(1, root.get("students").size());
        assertEquals("alice01", root.get("students").get(0).get("login").asText());
        assertEquals(10, root.get("students").get(0).get("commitCount").asInt());

        // Chunks
        assertEquals(1, root.get("chunks").size());
        assertEquals("FEATURE", root.get("chunks").get(0).get("classification").asText());
        assertEquals(42, root.get("chunks").get(0).get("linesChanged").asInt());

        // Commits
        assertEquals(1, root.get("commits").size());
        assertEquals("abc123", root.get("commits").get(0).get("commitHash").asText());
    }

    @Test
    void export_nullSections_omitsNullFields() throws IOException {
        ExportData data = new ExportData(null, null, null, null);

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        assertTrue(root.get("teams").isNull());
        assertTrue(root.get("students").isNull());
        assertTrue(root.get("chunks").isNull());
        assertTrue(root.get("commits").isNull());
    }

    @Test
    void export_timestamps_serializedAsIsoString() throws IOException {
        LocalDateTime ts = LocalDateTime.of(2025, 3, 20, 14, 45, 30);
        ExportData data = new ExportData(
                null, null,
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE", 0.9, 0.8, 0.7, 0.95,
                        "reasoning", "abc123", ts, 10)),
                null);

        byte[] result = JsonExporter.export(data);
        JsonNode root = mapper.readTree(result);

        String timestampValue = root.get("chunks").get(0).get("timestamp").asText();
        // Should be ISO string, not a numeric array
        assertTrue(timestampValue.contains("2025-03-20"), "Expected ISO date string but got: " + timestampValue);
        assertTrue(timestampValue.contains("14:45:30"), "Expected ISO time string but got: " + timestampValue);
    }
}

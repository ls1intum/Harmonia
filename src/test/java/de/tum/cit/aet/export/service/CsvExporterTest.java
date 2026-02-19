package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {

    @Test
    void export_allSections_containsAllHeaders() {
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                List.of(new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250)),
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE", 0.9, 0.8, 0.7, 0.95,
                        "Good work", "abc123", LocalDateTime.of(2025, 1, 15, 10, 30), 42)),
                List.of(new CommitExportRow("Team1", "abc123", "alice@test.com")));

        String csv = new String(CsvExporter.export(data), StandardCharsets.UTF_8);

        assertTrue(csv.contains("# Teams"));
        assertTrue(csv.contains("# Students"));
        assertTrue(csv.contains("# Chunks"));
        assertTrue(csv.contains("# Commits"));
        assertTrue(csv.contains("teamName,tutor,submissionCount,analysisStatus,cqi,cqiEffortBalance,cqiLocBalance,cqiTemporalSpread,cqiOwnershipSpread,isSuspicious,llmTotalTokens"));
        assertTrue(csv.contains("teamName,studentName,login,email,commitCount,linesAdded,linesDeleted,linesChanged"));
        assertTrue(csv.contains("teamName,authorName,authorEmail,classification,effortScore,complexity,novelty,confidence,reasoning,commitShas,timestamp,linesChanged"));
        assertTrue(csv.contains("teamName,commitHash,authorEmail"));
    }

    @Test
    void export_teamsOnly_containsOnlyTeamsSection() {
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                null, null, null);

        String csv = new String(CsvExporter.export(data), StandardCharsets.UTF_8);

        assertTrue(csv.contains("# Teams"));
        assertFalse(csv.contains("# Students"));
        assertFalse(csv.contains("# Chunks"));
        assertFalse(csv.contains("# Commits"));
        assertTrue(csv.contains("Team1,Tutor1,5,DONE,0.85,0.9,0.8,0.7,0.6,false,1000"));
    }

    @Test
    void export_emptyData_returnsEmptyBytes() {
        ExportData data = new ExportData(null, null, null, null);

        byte[] result = CsvExporter.export(data);

        assertEquals(0, result.length);
    }

    @Test
    void export_nullFields_rendersEmptyValues() {
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", null, null, null, null, null, null, null, null, null, null)),
                null, null, null);

        String csv = new String(CsvExporter.export(data), StandardCharsets.UTF_8);

        assertTrue(csv.contains("Team1,,,,,,,,,,"));
    }

    @Test
    void export_specialCharacters_escapesCorrectly() {
        ExportData data = new ExportData(
                null, null, null,
                List.of(new CommitExportRow("Team, \"Alpha\"", "abc123", "user@test.com")));

        String csv = new String(CsvExporter.export(data), StandardCharsets.UTF_8);

        // RFC 4180: commas and quotes must be enclosed in double quotes, inner quotes doubled
        assertTrue(csv.contains("\"Team, \"\"Alpha\"\"\""));
    }
}

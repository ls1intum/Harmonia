package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelExporterTest {

    @Test
    void export_allSections_createsFourSheets() throws IOException {
        ExportData data = new ExportData(
                List.of(sampleTeamRow()),
                List.of(sampleStudentRow()),
                List.of(sampleChunkRow()),
                List.of(new CommitExportRow("Team1", "abc123", "alice@test.com")));

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(4, workbook.getNumberOfSheets());
            assertEquals("Teams", workbook.getSheetName(0));
            assertEquals("Students", workbook.getSheetName(1));
            assertEquals("Chunks", workbook.getSheetName(2));
            assertEquals("Commits", workbook.getSheetName(3));
        }
    }

    @Test
    void export_teamsOnly_createsOnlyTeamsSheet() throws IOException {
        ExportData data = new ExportData(List.of(sampleTeamRow()), null, null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Teams", workbook.getSheetName(0));
        }
    }

    @Test
    void export_emptyData_createsEmptyWorkbook() throws IOException {
        ExportData data = new ExportData(null, null, null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(0, workbook.getNumberOfSheets());
        }
    }

    @Test
    void export_teamData_writesAllCellValues() throws IOException {
        ExportData data = new ExportData(List.of(sampleTeamRow()), null, null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            XSSFSheet sheet = workbook.getSheet("Teams");
            // Header row - all 17 columns
            String[] expectedHeaders = {"teamName", "shortName", "tutor", "repositoryUrl",
                    "submissionCount", "analysisStatus",
                    "cqi", "cqiEffortBalance", "cqiLocBalance", "cqiTemporalSpread",
                    "cqiOwnershipSpread", "cqiPairProgramming", "cqiPairProgrammingStatus",
                    "isSuspicious", "llmTotalTokens"};
            for (int i = 0; i < expectedHeaders.length; i++) {
                assertEquals(expectedHeaders[i], sheet.getRow(0).getCell(i).getStringCellValue(),
                        "Header mismatch at column " + i);
            }
            // Data row - all values
            var row = sheet.getRow(1);
            assertEquals("Team1", row.getCell(0).getStringCellValue());
            assertEquals("t1", row.getCell(1).getStringCellValue());
            assertEquals("Tutor1", row.getCell(2).getStringCellValue());
            assertEquals("https://git.example.com/team1", row.getCell(3).getStringCellValue());
            assertEquals(5.0, row.getCell(4).getNumericCellValue());
            assertEquals("DONE", row.getCell(5).getStringCellValue());
            assertEquals(0.85, row.getCell(6).getNumericCellValue(), 0.001);
            assertEquals(0.9, row.getCell(7).getNumericCellValue(), 0.001);
            assertEquals(0.8, row.getCell(8).getNumericCellValue(), 0.001);
            assertEquals(0.7, row.getCell(9).getNumericCellValue(), 0.001);
            assertEquals(0.6, row.getCell(10).getNumericCellValue(), 0.001);
            assertEquals("", row.getCell(11).getStringCellValue()); // cqiPairProgramming null -> ""
            assertEquals("", row.getCell(12).getStringCellValue()); // cqiPairProgrammingStatus null
            assertFalse(row.getCell(13).getBooleanCellValue());
            assertEquals(1000.0, row.getCell(14).getNumericCellValue());
        }
    }

    @Test
    void export_studentData_writesAllCellValues() throws IOException {
        ExportData data = new ExportData(null, List.of(sampleStudentRow()), null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            XSSFSheet sheet = workbook.getSheet("Students");
            String[] expectedHeaders = {"teamName", "studentName", "login", "email",
                    "commitCount", "linesAdded", "linesDeleted", "linesChanged"};
            for (int i = 0; i < expectedHeaders.length; i++) {
                assertEquals(expectedHeaders[i], sheet.getRow(0).getCell(i).getStringCellValue(),
                        "Header mismatch at column " + i);
            }
            var row = sheet.getRow(1);
            assertEquals("Team1", row.getCell(0).getStringCellValue());
            assertEquals("Alice", row.getCell(1).getStringCellValue());
            assertEquals("alice01", row.getCell(2).getStringCellValue());
            assertEquals("alice@test.com", row.getCell(3).getStringCellValue());
            assertEquals(10.0, row.getCell(4).getNumericCellValue());
            assertEquals(200.0, row.getCell(5).getNumericCellValue());
            assertEquals(50.0, row.getCell(6).getNumericCellValue());
            assertEquals(250.0, row.getCell(7).getNumericCellValue());
        }
    }

    @Test
    void export_chunkData_writesAllCellValues() throws IOException {
        ExportData data = new ExportData(null, null, List.of(sampleChunkRow()), null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            XSSFSheet sheet = workbook.getSheet("Chunks");
            String[] expectedHeaders = {"teamName", "authorName", "authorEmail", "classification",
                    "effortScore", "complexity", "novelty", "confidence", "reasoning",
                    "commitShas", "commitMessages", "timestamp", "linesChanged",
                    "isBundled", "chunkIndex", "totalChunks",
                    "isError", "errorMessage",
                    "llmModel", "llmPromptTokens", "llmCompletionTokens", "llmTotalTokens",
                    "llmUsageAvailable"};
            for (int i = 0; i < expectedHeaders.length; i++) {
                assertEquals(expectedHeaders[i], sheet.getRow(0).getCell(i).getStringCellValue(),
                        "Header mismatch at column " + i);
            }
            var row = sheet.getRow(1);
            assertEquals("Team1", row.getCell(0).getStringCellValue());
            assertEquals("Alice", row.getCell(1).getStringCellValue());
            assertEquals("alice@test.com", row.getCell(2).getStringCellValue());
            assertEquals("FEATURE", row.getCell(3).getStringCellValue());
            assertEquals(0.9, row.getCell(4).getNumericCellValue(), 0.001);
            assertEquals(0.8, row.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(0.7, row.getCell(6).getNumericCellValue(), 0.001);
            assertEquals(0.95, row.getCell(7).getNumericCellValue(), 0.001);
            assertEquals("Good work", row.getCell(8).getStringCellValue());
            assertEquals("abc123", row.getCell(9).getStringCellValue());
            assertEquals("[\"init commit\"]", row.getCell(10).getStringCellValue());
            assertTrue(row.getCell(11).getStringCellValue().contains("2025-01-15"));
            assertEquals(42.0, row.getCell(12).getNumericCellValue());
            assertFalse(row.getCell(13).getBooleanCellValue());
            assertEquals(0.0, row.getCell(14).getNumericCellValue());
            assertEquals(1.0, row.getCell(15).getNumericCellValue());
            assertFalse(row.getCell(16).getBooleanCellValue());
            assertEquals("", row.getCell(17).getStringCellValue());
            assertEquals("gpt-4", row.getCell(18).getStringCellValue());
            assertEquals(100.0, row.getCell(19).getNumericCellValue());
            assertEquals(200.0, row.getCell(20).getNumericCellValue());
            assertEquals(300.0, row.getCell(21).getNumericCellValue());
            assertTrue(row.getCell(22).getBooleanCellValue());
        }
    }

    @Test
    void export_commitData_writesAllCellValues() throws IOException {
        ExportData data = new ExportData(null, null, null,
                List.of(new CommitExportRow("Team1", "abc123", "alice@test.com")));

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            XSSFSheet sheet = workbook.getSheet("Commits");
            assertEquals("teamName", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("commitHash", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("authorEmail", sheet.getRow(0).getCell(2).getStringCellValue());
            var row = sheet.getRow(1);
            assertEquals("Team1", row.getCell(0).getStringCellValue());
            assertEquals("abc123", row.getCell(1).getStringCellValue());
            assertEquals("alice@test.com", row.getCell(2).getStringCellValue());
        }
    }

    private static TeamExportRow sampleTeamRow() {
        return new TeamExportRow("Team1", "t1", "Tutor1", "https://git.example.com/team1",
                5, "DONE", 0.85,
                0.9, 0.8, 0.7, 0.6, null, null,
                false, 1000L);
    }

    private static StudentExportRow sampleStudentRow() {
        return new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250);
    }

    private static ChunkExportRow sampleChunkRow() {
        return new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE",
                0.9, 0.8, 0.7, 0.95, "Good work", "abc123", "[\"init commit\"]",
                LocalDateTime.of(2025, 1, 15, 10, 30), 42,
                false, 0, 1, false, null,
                "gpt-4", 100L, 200L, 300L, true);
    }
}

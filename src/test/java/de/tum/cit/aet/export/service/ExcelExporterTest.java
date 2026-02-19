package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;
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
                List.of(new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250)),
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
    void export_teamData_writesCorrectCellValues() throws IOException {
        ExportData data = new ExportData(List.of(sampleTeamRow()), null, null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("Teams");
            // Header row
            assertEquals("teamName", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("shortName", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("tutor", sheet.getRow(0).getCell(2).getStringCellValue());
            // Data row
            assertEquals("Team1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("t1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Tutor1", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(0.85, sheet.getRow(1).getCell(6).getNumericCellValue(), 0.001);
            assertFalse(sheet.getRow(1).getCell(15).getBooleanCellValue());
            assertEquals(1000.0, sheet.getRow(1).getCell(16).getNumericCellValue());
        }
    }

    private static TeamExportRow sampleTeamRow() {
        return new TeamExportRow("Team1", "t1", "Tutor1", "https://git.example.com/team1",
                5, "DONE", 0.85, 80.0, 1.0,
                0.9, 0.8, 0.7, 0.6, null, null,
                false, 1000L);
    }

    private static ChunkExportRow sampleChunkRow() {
        return new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE",
                0.9, 0.8, 0.7, 0.95, "Good work", "abc123", "[\"init commit\"]",
                LocalDateTime.of(2025, 1, 15, 10, 30), 42,
                false, 0, 1, false, null, false,
                "gpt-4", 100L, 200L, 300L);
    }
}

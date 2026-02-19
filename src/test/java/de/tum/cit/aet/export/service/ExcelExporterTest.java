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
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                List.of(new StudentExportRow("Team1", "Alice", "alice01", "alice@test.com", 10, 200, 50, 250)),
                List.of(new ChunkExportRow("Team1", "Alice", "alice@test.com", "FEATURE", 0.9, 0.8, 0.7, 0.95,
                        "Good work", "abc123", LocalDateTime.of(2025, 1, 15, 10, 30), 42)),
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
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                null, null, null);

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
        ExportData data = new ExportData(
                List.of(new TeamExportRow("Team1", "Tutor1", 5, "DONE", 0.85, 0.9, 0.8, 0.7, 0.6, false, 1000L)),
                null, null, null);

        byte[] result = ExcelExporter.export(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("Teams");
            // Header row
            assertEquals("teamName", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("tutor", sheet.getRow(0).getCell(1).getStringCellValue());
            // Data row
            assertEquals("Team1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Tutor1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(5.0, sheet.getRow(1).getCell(2).getNumericCellValue());
            assertEquals("DONE", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals(0.85, sheet.getRow(1).getCell(4).getNumericCellValue(), 0.001);
            assertFalse(sheet.getRow(1).getCell(9).getBooleanCellValue());
            assertEquals(1000.0, sheet.getRow(1).getCell(10).getNumericCellValue());
        }
    }
}

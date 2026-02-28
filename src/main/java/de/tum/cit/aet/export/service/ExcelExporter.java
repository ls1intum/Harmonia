package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Utility class that serializes {@link ExportData} into an Excel workbook (.xlsx).
 * Creates a separate sheet for each data scope (teams, students, chunks, commits).
 */
public final class ExcelExporter {

    private ExcelExporter() {
    }

    /**
     * Exports the given data as an Excel workbook with one sheet per data scope.
     *
     * @param data the collected export data
     * @return the XLSX content as a byte array
     * @throws UncheckedIOException if writing the workbook fails
     */
    public static byte[] export(ExportData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 1) Write each data scope into its own sheet
            writeTeamsSheet(workbook, data);
            writeStudentsSheet(workbook, data);
            writeChunksSheet(workbook, data);
            writeCommitsSheet(workbook, data);

            // 2) Serialize the workbook to a byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Excel export", e);
        }
    }

    private static void writeTeamsSheet(XSSFWorkbook workbook, ExportData data) {
        if (data.teams() == null || data.teams().isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("Teams");
        String[] headers = {"teamName", "shortName", "tutor", "repositoryUrl",
                "submissionCount", "analysisStatus",
                "cqi", "cqiEffortBalance", "cqiLocBalance", "cqiTemporalSpread",
                "cqiOwnershipSpread", "cqiPairProgramming", "cqiPairProgrammingStatus",
                "isSuspicious", "llmTotalTokens"};
        writeHeaderRow(sheet, headers);

        int row = 1;
        for (TeamExportRow r : data.teams()) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(str(r.teamName()));
            dataRow.createCell(1).setCellValue(str(r.shortName()));
            dataRow.createCell(2).setCellValue(str(r.tutor()));
            dataRow.createCell(3).setCellValue(str(r.repositoryUrl()));
            setCellNumeric(dataRow, 4, r.submissionCount());
            dataRow.createCell(5).setCellValue(str(r.analysisStatus()));
            setCellNumeric(dataRow, 6, r.cqi());
            setCellNumeric(dataRow, 7, r.cqiEffortBalance());
            setCellNumeric(dataRow, 8, r.cqiLocBalance());
            setCellNumeric(dataRow, 9, r.cqiTemporalSpread());
            setCellNumeric(dataRow, 10, r.cqiOwnershipSpread());
            setCellNumeric(dataRow, 11, r.cqiPairProgramming());
            dataRow.createCell(12).setCellValue(str(r.cqiPairProgrammingStatus()));
            dataRow.createCell(13).setCellValue(r.isSuspicious() != null && r.isSuspicious());
            setCellNumeric(dataRow, 14, r.llmTotalTokens());
        }
    }

    private static void writeStudentsSheet(XSSFWorkbook workbook, ExportData data) {
        if (data.students() == null || data.students().isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("Students");
        String[] headers = {"teamName", "studentName", "login", "email",
                "commitCount", "linesAdded", "linesDeleted", "linesChanged"};
        writeHeaderRow(sheet, headers);

        int row = 1;
        for (StudentExportRow r : data.students()) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(str(r.teamName()));
            dataRow.createCell(1).setCellValue(str(r.studentName()));
            dataRow.createCell(2).setCellValue(str(r.login()));
            dataRow.createCell(3).setCellValue(str(r.email()));
            setCellNumeric(dataRow, 4, r.commitCount());
            setCellNumeric(dataRow, 5, r.linesAdded());
            setCellNumeric(dataRow, 6, r.linesDeleted());
            setCellNumeric(dataRow, 7, r.linesChanged());
        }
    }

    private static void writeChunksSheet(XSSFWorkbook workbook, ExportData data) {
        if (data.chunks() == null || data.chunks().isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("Chunks");
        String[] headers = {"teamName", "authorName", "authorEmail", "classification",
                "effortScore", "complexity", "novelty", "confidence", "reasoning",
                "commitShas", "commitMessages", "timestamp", "linesChanged",
                "isBundled", "chunkIndex", "totalChunks",
                "isError", "errorMessage",
                "llmModel", "llmPromptTokens", "llmCompletionTokens", "llmTotalTokens",
                "llmUsageAvailable"};
        writeHeaderRow(sheet, headers);

        int row = 1;
        for (ChunkExportRow r : data.chunks()) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(str(r.teamName()));
            dataRow.createCell(1).setCellValue(str(r.authorName()));
            dataRow.createCell(2).setCellValue(str(r.authorEmail()));
            dataRow.createCell(3).setCellValue(str(r.classification()));
            setCellNumeric(dataRow, 4, r.effortScore());
            setCellNumeric(dataRow, 5, r.complexity());
            setCellNumeric(dataRow, 6, r.novelty());
            setCellNumeric(dataRow, 7, r.confidence());
            dataRow.createCell(8).setCellValue(str(r.reasoning()));
            dataRow.createCell(9).setCellValue(str(r.commitShas()));
            dataRow.createCell(10).setCellValue(str(r.commitMessages()));
            dataRow.createCell(11).setCellValue(r.timestamp() != null ? r.timestamp().toString() : "");
            setCellNumeric(dataRow, 12, r.linesChanged());
            dataRow.createCell(13).setCellValue(r.isBundled() != null && r.isBundled());
            setCellNumeric(dataRow, 14, r.chunkIndex());
            setCellNumeric(dataRow, 15, r.totalChunks());
            dataRow.createCell(16).setCellValue(r.isError() != null && r.isError());
            dataRow.createCell(17).setCellValue(str(r.errorMessage()));
            dataRow.createCell(18).setCellValue(str(r.llmModel()));
            setCellNumeric(dataRow, 19, r.llmPromptTokens());
            setCellNumeric(dataRow, 20, r.llmCompletionTokens());
            setCellNumeric(dataRow, 21, r.llmTotalTokens());
            dataRow.createCell(22).setCellValue(r.llmUsageAvailable() != null && r.llmUsageAvailable());
        }
    }

    private static void writeCommitsSheet(XSSFWorkbook workbook, ExportData data) {
        if (data.commits() == null || data.commits().isEmpty()) {
            return;
        }
        XSSFSheet sheet = workbook.createSheet("Commits");
        String[] headers = {"teamName", "commitHash", "authorEmail"};
        writeHeaderRow(sheet, headers);

        int row = 1;
        for (CommitExportRow r : data.commits()) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(str(r.teamName()));
            dataRow.createCell(1).setCellValue(str(r.commitHash()));
            dataRow.createCell(2).setCellValue(str(r.authorEmail()));
        }
    }

    private static void writeHeaderRow(XSSFSheet sheet, String[] headers) {
        XSSFRow headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private static void setCellNumeric(XSSFRow row, int col, Number value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        } else {
            row.createCell(col).setCellValue("");
        }
    }
}

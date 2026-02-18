package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ExcelExporter {

    private ExcelExporter() {
    }

    /**
     * Export data as an Excel workbook with separate sheets per section.
     *
     * @param data the export data
     * @return XLSX content as byte array
     * @throws IOException if writing the workbook fails
     */
    public static byte[] export(ExportData data) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            if (data.getTeams() != null && !data.getTeams().isEmpty()) {
                XSSFSheet sheet = workbook.createSheet("Teams");
                int row = 0;
                var header = sheet.createRow(row++);
                String[] teamHeaders = {"teamName", "tutor", "submissionCount", "analysisStatus",
                        "cqi", "cqiEffortBalance", "cqiLocBalance", "cqiTemporalSpread",
                        "cqiOwnershipSpread", "isSuspicious", "llmTotalTokens"};
                for (int i = 0; i < teamHeaders.length; i++) {
                    header.createCell(i).setCellValue(teamHeaders[i]);
                }
                for (TeamExportRow r : data.getTeams()) {
                    var dataRow = sheet.createRow(row++);
                    dataRow.createCell(0).setCellValue(str(r.teamName()));
                    dataRow.createCell(1).setCellValue(str(r.tutor()));
                    setCellNumeric(dataRow, 2, r.submissionCount());
                    dataRow.createCell(3).setCellValue(str(r.analysisStatus()));
                    setCellNumeric(dataRow, 4, r.cqi());
                    setCellNumeric(dataRow, 5, r.cqiEffortBalance());
                    setCellNumeric(dataRow, 6, r.cqiLocBalance());
                    setCellNumeric(dataRow, 7, r.cqiTemporalSpread());
                    setCellNumeric(dataRow, 8, r.cqiOwnershipSpread());
                    dataRow.createCell(9).setCellValue(r.isSuspicious() != null && r.isSuspicious());
                    setCellNumeric(dataRow, 10, r.llmTotalTokens());
                }
            }

            if (data.getStudents() != null && !data.getStudents().isEmpty()) {
                XSSFSheet sheet = workbook.createSheet("Students");
                int row = 0;
                var header = sheet.createRow(row++);
                String[] studentHeaders = {"teamName", "studentName", "login", "email",
                        "commitCount", "linesAdded", "linesDeleted", "linesChanged"};
                for (int i = 0; i < studentHeaders.length; i++) {
                    header.createCell(i).setCellValue(studentHeaders[i]);
                }
                for (StudentExportRow r : data.getStudents()) {
                    var dataRow = sheet.createRow(row++);
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

            if (data.getChunks() != null && !data.getChunks().isEmpty()) {
                XSSFSheet sheet = workbook.createSheet("Chunks");
                int row = 0;
                var header = sheet.createRow(row++);
                String[] chunkHeaders = {"teamName", "authorName", "authorEmail", "classification",
                        "effortScore", "complexity", "novelty", "confidence", "reasoning",
                        "commitShas", "timestamp", "linesChanged"};
                for (int i = 0; i < chunkHeaders.length; i++) {
                    header.createCell(i).setCellValue(chunkHeaders[i]);
                }
                for (ChunkExportRow r : data.getChunks()) {
                    var dataRow = sheet.createRow(row++);
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
                    dataRow.createCell(10).setCellValue(r.timestamp() != null ? r.timestamp().toString() : "");
                    setCellNumeric(dataRow, 11, r.linesChanged());
                }
            }

            if (data.getCommits() != null && !data.getCommits().isEmpty()) {
                XSSFSheet sheet = workbook.createSheet("Commits");
                int row = 0;
                var header = sheet.createRow(row++);
                String[] commitHeaders = {"teamName", "commitHash", "authorEmail"};
                for (int i = 0; i < commitHeaders.length; i++) {
                    header.createCell(i).setCellValue(commitHeaders[i]);
                }
                for (CommitExportRow r : data.getCommits()) {
                    var dataRow = sheet.createRow(row++);
                    dataRow.createCell(0).setCellValue(str(r.teamName()));
                    dataRow.createCell(1).setCellValue(str(r.commitHash()));
                    dataRow.createCell(2).setCellValue(str(r.authorEmail()));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private static void setCellNumeric(org.apache.poi.xssf.usermodel.XSSFRow row, int col, Number value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        } else {
            row.createCell(col).setCellValue("");
        }
    }
}

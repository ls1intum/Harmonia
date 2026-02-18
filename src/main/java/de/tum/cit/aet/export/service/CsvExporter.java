package de.tum.cit.aet.export.service;

import de.tum.cit.aet.export.dto.*;

import java.nio.charset.StandardCharsets;

public final class CsvExporter {

    private CsvExporter() {
    }

    /**
     * Export data as CSV bytes with section headers.
     *
     * @param data the export data
     * @return CSV content as byte array
     */
    public static byte[] export(ExportData data) {
        StringBuilder sb = new StringBuilder();

        if (data.getTeams() != null && !data.getTeams().isEmpty()) {
            sb.append("# Teams\n");
            sb.append("teamName,tutor,submissionCount,analysisStatus,cqi,cqiEffortBalance,cqiLocBalance,cqiTemporalSpread,cqiOwnershipSpread,isSuspicious,llmTotalTokens\n");
            for (TeamExportRow row : data.getTeams()) {
                sb.append(escape(row.teamName())).append(',');
                sb.append(escape(row.tutor())).append(',');
                sb.append(val(row.submissionCount())).append(',');
                sb.append(escape(row.analysisStatus())).append(',');
                sb.append(val(row.cqi())).append(',');
                sb.append(val(row.cqiEffortBalance())).append(',');
                sb.append(val(row.cqiLocBalance())).append(',');
                sb.append(val(row.cqiTemporalSpread())).append(',');
                sb.append(val(row.cqiOwnershipSpread())).append(',');
                sb.append(val(row.isSuspicious())).append(',');
                sb.append(val(row.llmTotalTokens())).append('\n');
            }
            sb.append('\n');
        }

        if (data.getStudents() != null && !data.getStudents().isEmpty()) {
            sb.append("# Students\n");
            sb.append("teamName,studentName,login,email,commitCount,linesAdded,linesDeleted,linesChanged\n");
            for (StudentExportRow row : data.getStudents()) {
                sb.append(escape(row.teamName())).append(',');
                sb.append(escape(row.studentName())).append(',');
                sb.append(escape(row.login())).append(',');
                sb.append(escape(row.email())).append(',');
                sb.append(val(row.commitCount())).append(',');
                sb.append(val(row.linesAdded())).append(',');
                sb.append(val(row.linesDeleted())).append(',');
                sb.append(val(row.linesChanged())).append('\n');
            }
            sb.append('\n');
        }

        if (data.getChunks() != null && !data.getChunks().isEmpty()) {
            sb.append("# Chunks\n");
            sb.append("teamName,authorName,authorEmail,classification,effortScore,complexity,novelty,confidence,reasoning,commitShas,timestamp,linesChanged\n");
            for (ChunkExportRow row : data.getChunks()) {
                sb.append(escape(row.teamName())).append(',');
                sb.append(escape(row.authorName())).append(',');
                sb.append(escape(row.authorEmail())).append(',');
                sb.append(escape(row.classification())).append(',');
                sb.append(val(row.effortScore())).append(',');
                sb.append(val(row.complexity())).append(',');
                sb.append(val(row.novelty())).append(',');
                sb.append(val(row.confidence())).append(',');
                sb.append(escape(row.reasoning())).append(',');
                sb.append(escape(row.commitShas())).append(',');
                sb.append(val(row.timestamp())).append(',');
                sb.append(val(row.linesChanged())).append('\n');
            }
            sb.append('\n');
        }

        if (data.getCommits() != null && !data.getCommits().isEmpty()) {
            sb.append("# Commits\n");
            sb.append("teamName,commitHash,authorEmail\n");
            for (CommitExportRow row : data.getCommits()) {
                sb.append(escape(row.teamName())).append(',');
                sb.append(escape(row.commitHash())).append(',');
                sb.append(escape(row.authorEmail())).append('\n');
            }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String val(Object value) {
        return value == null ? "" : value.toString();
    }
}

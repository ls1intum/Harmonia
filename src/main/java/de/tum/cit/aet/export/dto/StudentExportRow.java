package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentExportRow(
        String teamName,
        String studentName,
        String login,
        String email,
        Integer commitCount,
        Integer linesAdded,
        Integer linesDeleted,
        Integer linesChanged) {
}

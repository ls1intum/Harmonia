package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitExportRow(
        String teamName,
        String commitHash,
        String authorEmail) {
}

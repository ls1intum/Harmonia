package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export row for commit-level data.
 *
 * @param teamName    team the commit belongs to
 * @param commitHash  the commit SHA hash
 * @param authorEmail the commit author's email
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitExportRow(
        String teamName,
        String commitHash,
        String authorEmail) {
}

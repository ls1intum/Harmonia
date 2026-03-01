package de.tum.cit.aet.analysis.dto;

import java.util.Map;

/**
 * Result of mapping commits to authors, including orphan commits.
 *
 * @param commitToAuthor     map of commit hash to student ID for assigned commits
 * @param orphanCommitEmails map of commit hash to git author email for orphan commits
 * @param commitToEmail      map of commit hash to display email for assigned commits
 */
public record CommitMappingResultDTO(
        Map<String, Long> commitToAuthor,
        Map<String, String> orphanCommitEmails,
        Map<String, String> commitToEmail) {
}

package de.tum.cit.aet.analysis.dto;

import java.util.Map;

/**
 * Result of mapping commits to authors, including orphan commits.
 *
 * @param commitToAuthor     hash -> studentId for assigned commits
 * @param orphanCommitEmails hash -> git author email for orphan commits (template commits excluded)
 * @param commitToEmail      hash -> display email for assigned commits
 */
public record CommitMappingResultDTO(
        Map<String, Long> commitToAuthor,
        Map<String, String> orphanCommitEmails,
        Map<String, String> commitToEmail) {
}

package de.tum.cit.aet.analysis.dto;

import java.util.Map;
import java.util.Set;

/**
 * Result of mapping all commits (from full git history walk) to authors.
 *
 * @param commitToAuthor       map of commit hash to student ID for assigned commits
 * @param orphanCommitEmails   map of commit hash to git author email for unassigned commits
 * @param commitToVcsEmail     map of commit hash to display email for assigned commits
 * @param templateCommitHashes hashes of root commits whose author could not be resolved,
 *                             typically the course-template initial commit
 */
public record FullCommitMappingResultDTO(
        Map<String, Long> commitToAuthor,
        Map<String, String> orphanCommitEmails,
        Map<String, String> commitToVcsEmail,
        Set<String> templateCommitHashes) {

    public static FullCommitMappingResultDTO empty() {
        return new FullCommitMappingResultDTO(Map.of(), Map.of(), Map.of(), Set.of());
    }
}

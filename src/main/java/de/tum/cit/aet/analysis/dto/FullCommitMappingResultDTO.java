package de.tum.cit.aet.analysis.dto;

import java.util.Map;
import java.util.Set;

/**
 * Result of mapping ALL commits (from full git history walk) to authors.
 *
 * @param commitToAuthor       hash -> studentId for all assigned commits
 * @param orphanCommitEmails   hash -> git author email for commits that could not be assigned
 * @param commitToVcsEmail     hash -> display email for all assigned commits
 *                             (VCS email for Tier 1 anchors, Artemis email for Tier 2/3)
 * @param templateCommitHashes hashes of root commits (parentCount == 0) whose author
 *                             could not be resolved to any student — typically the
 *                             course-template initial commit
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

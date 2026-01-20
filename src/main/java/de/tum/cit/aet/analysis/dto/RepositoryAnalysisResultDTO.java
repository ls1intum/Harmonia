package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for repository analysis results containing both student contributions
 * and orphan commits (commits that couldn't be attributed to any student).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryAnalysisResultDTO(
        Map<Long, AuthorContributionDTO> contributions,
        List<OrphanCommitDTO> orphanCommits) {
    public static RepositoryAnalysisResultDTO empty() {
        return new RepositoryAnalysisResultDTO(Map.of(), List.of());
    }
}

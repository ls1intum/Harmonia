package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing an author's contribution statistics.
 *
 * @param linesAdded   The number of lines added by the author.
 * @param linesDeleted The number of lines deleted by the author.
 * @param commitCount  The number of commits made by the author.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorContributionDTO(
        Integer linesAdded,
        Integer linesDeleted,
        Integer commitCount
) {
}

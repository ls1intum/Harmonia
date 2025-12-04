package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Commit classification result.
 *
 * @param label the classification label
 * @param confidence confidence score (0.0-1.0)
 * @param reasoning explanation for the classification
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitClassificationDTO(
    CommitLabel label,
    double confidence,
    String reasoning
) {
}

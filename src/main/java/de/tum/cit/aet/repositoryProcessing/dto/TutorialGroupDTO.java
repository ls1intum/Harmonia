package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Artemis API tutorial group response.
 * Represents a tutorial group that teams belong to.
 *
 * @param id The unique identifier of the tutorial group.
 * @param title The title/name of the tutorial group.
 * @param courseId The course ID this tutorial group belongs to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TutorialGroupDTO(
        Long id,
        String title,
        Long courseId
) {
}

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Artemis API exercise response.
 *
 * @param id       the unique exercise identifier
 * @param courseId the course ID this exercise belongs to
 * @param title   the exercise title
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDTO(
        Long id,
        Long courseId,
        String title
) {
}

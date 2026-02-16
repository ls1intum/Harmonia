package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Artemis API exercise response.
 * Used to get course ID from exercise ID.
 *
 * @param id The unique identifier of the exercise.
 * @param courseId The course ID this exercise belongs to.
 * @param title The title of the exercise.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDTO(
        Long id,
        Long courseId,
        String title
) {
}

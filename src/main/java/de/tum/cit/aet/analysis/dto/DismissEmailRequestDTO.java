package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for dismissing an orphan email without student assignment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DismissEmailRequestDTO(
        String gitEmail,
        Long teamParticipationId) {
}

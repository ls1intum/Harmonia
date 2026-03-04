package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for creating a new email mapping.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateEmailMappingRequestDTO(
        String gitEmail,
        Long studentId,
        String studentName,
        Long teamParticipationId) {
}

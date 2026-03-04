package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * DTO returned for each persisted mapping.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailMappingDTO(
        UUID id,
        Long exerciseId,
        String gitEmail,
        Long studentId,
        String studentName,
        Boolean isDismissed) {
}

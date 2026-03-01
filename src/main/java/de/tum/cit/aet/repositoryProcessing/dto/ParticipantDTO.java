package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a participant (student or tutor).
 *
 * @param id    the unique participant identifier
 * @param login the participant's login username
 * @param name  the participant's full name
 * @param email the participant's email address
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipantDTO(
        Long id,
        String login,
        String name,
        String email
) {
}

package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a Participant (e.g., a student or tutor).
 * Used to convey basic information about an individual user.
 *
 * @param id The unique identifier of the participant.
 * @param login The unique login name (e.g., username) of the participant.
 * @param name The full name of the participant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipantDTO(
        Long id,
        String login,
        String name,
        String email
) {
}

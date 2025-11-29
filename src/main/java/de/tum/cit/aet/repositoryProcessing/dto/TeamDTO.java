package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO for a Team.
 * Used to represent team information, including its members and owner.
 *
 * @param id        The unique identifier of the team.
 * @param name      The full name of the team.
 * @param shortName The shortened name for the team.
 * @param students  A list of the team members (participants).
 * @param owner     The tutor for the team.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDTO(
        Long id,
        String name,
        String shortName,
        List<ParticipantDTO> students,
        ParticipantDTO owner
) {
}

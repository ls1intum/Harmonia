package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO for an Artemis team including its members and tutor.
 *
 * @param id        the unique team identifier
 * @param name      the full team name
 * @param shortName the shortened team name
 * @param students  list of team members
 * @param owner     the tutor assigned to this team
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

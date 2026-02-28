package de.tum.cit.aet.pairProgramming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Aggregated attendance schedule for all teams in an exercise.
 *
 * @param teams map of team name to attendance data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamsScheduleDTO(
        Map<String, TeamAttendanceDTO> teams
) {
}

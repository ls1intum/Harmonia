package de.tum.cit.aet.pairProgramming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamsScheduleDTO(
        Map<String, TeamAttendanceDTO> teams
) {
}

package de.tum.cit.aet.dataProcessing.dto;

import java.util.Map;

public record TeamsScheduleDTO(
        Map<String, TeamAttendanceDTO> teams
) {
}

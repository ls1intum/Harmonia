package de.tum.cit.aet.pairProgramming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamAttendanceDTO(
        Map<OffsetDateTime, Boolean> student1Attendance,
        Map<OffsetDateTime, Boolean> student2Attendance,
        boolean pairedMandatorySessions,
        List<OffsetDateTime> pairedSessions
) {
}

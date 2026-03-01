package de.tum.cit.aet.pairProgramming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Attendance data for a pair of students in a team.
 *
 * @param student1Attendance       session date to attendance flag for the first student
 * @param student2Attendance       session date to attendance flag for the second student
 * @param pairedMandatorySessions  whether both students attended all mandatory sessions together
 * @param pairedSessions           list of session dates where both students were present
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamAttendanceDTO(
        Map<OffsetDateTime, Boolean> student1Attendance,
        Map<OffsetDateTime, Boolean> student2Attendance,
        boolean pairedMandatorySessions,
        List<OffsetDateTime> pairedSessions
) {
}

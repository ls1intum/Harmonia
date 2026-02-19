package de.tum.cit.aet.dataProcessing.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record TeamAttendanceDTO(
        Map<OffsetDateTime, Boolean> student1Attendance,
        Map<OffsetDateTime, Boolean> student2Attendance,
        boolean pairedMandatorySessions,
        List<OffsetDateTime> pairedSessions
) {
}

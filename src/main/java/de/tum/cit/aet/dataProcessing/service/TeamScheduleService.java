package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TeamScheduleService {

    private final Map<String, TeamAttendanceDTO> teamsByNormalizedName = new ConcurrentHashMap<>();

    public void update(TeamsScheduleDTO schedule) {
        teamsByNormalizedName.clear();
        if (schedule == null || schedule.teams() == null) {
            return;
        }
        schedule.teams().forEach((teamName, attendance) -> {
            if (teamName != null && attendance != null) {
                teamsByNormalizedName.put(normalize(teamName), attendance);
            }
        });
    }

    public TeamAttendanceDTO getTeamAttendance(String teamName) {
        if (teamName == null) {
            return null;
        }
        return teamsByNormalizedName.get(normalize(teamName));
    }

    // TODO: Depending on CommitInfo, change back to ZonedDateTime/OffsetDateTime
    public Set<LocalDateTime> getClassDates(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        if (attendance == null) {
            return Set.of();
        }
        Map<OffsetDateTime, Boolean> sessionMap = firstNonEmpty(
                attendance.student1Attendance(),
                attendance.student2Attendance()
        );
        if (sessionMap.isEmpty()) {
            return Set.of();
        }
        return sessionMap.keySet().stream()
                .map(OffsetDateTime::toLocalDateTime)
                .collect(Collectors.toSet());
    }

    // TODO: Depending on CommitInfo, change back to ZonedDateTime/OffsetDateTime
    public Set<LocalDateTime> getPairedSessions(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        if (attendance == null || attendance.pairedSessions() == null) {
            return Set.of();
        }
        return attendance.pairedSessions().stream()
                .map(OffsetDateTime::toLocalDateTime)
                .collect(Collectors.toSet());
    }

    public boolean isPairedAtLeastTwoOfThree(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        return attendance != null && attendance.pairedAtLeastTwoOfThree();
    }

    private Map<OffsetDateTime, Boolean> firstNonEmpty(
            Map<OffsetDateTime, Boolean> first,
            Map<OffsetDateTime, Boolean> second
    ) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return Map.of();
    }

    private String normalize(String teamName) {
        return teamName == null ? "" : teamName.trim().toLowerCase(Locale.ROOT);
    }
}

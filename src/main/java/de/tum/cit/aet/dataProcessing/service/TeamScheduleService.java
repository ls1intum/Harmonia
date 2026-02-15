package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TeamScheduleService {

    private final Map<String, TeamAttendanceDTO> teamsByNormalizedName = new ConcurrentHashMap<>();

    /**
     * Updates the team schedule with the provided schedule data.
     *
     * @param schedule the teams schedule DTO
     */
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

    /**
     * Retrieves the attendance information for a specific team.
     *
     * @param teamName the name of the team
     * @return the team attendance DTO or null if not found
     */
    public TeamAttendanceDTO getTeamAttendance(String teamName) {
        if (teamName == null) {
            return null;
        }
        return teamsByNormalizedName.get(normalize(teamName));
    }

    /**
     * Retrieves the class dates for a specific team.
     *
     * @param teamName the name of the team
     * @return a set of class dates
     */
    public Set<OffsetDateTime> getClassDates(String teamName) {
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
        return new HashSet<>(sessionMap.keySet());
    }

    /**
     * Retrieves the paired sessions for a specific team.
     *
     * @param teamName the name of the team
     * @return a set of paired sessions
     */
    public Set<OffsetDateTime> getPairedSessions(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        if (attendance == null || attendance.pairedSessions() == null) {
            return Set.of();
        }
        return new HashSet<>(attendance.pairedSessions());
    }

    public boolean isPairedAtLeastTwoOfThree(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        return attendance == null || attendance.pairedAtLeastTwoOfThree();
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

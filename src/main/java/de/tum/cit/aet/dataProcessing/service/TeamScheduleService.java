package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import org.springframework.stereotype.Service;
import static de.tum.cit.aet.dataProcessing.util.AttendanceUtils.normalize;

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
     * Checks if attendance data exists for a specific team.
     *
     * @param teamName the team name to look up
     * @return true if the team exists in uploaded attendance data, false otherwise
     */
    public boolean hasTeamAttendance(String teamName) {
        if (teamName == null) {
            return false;
        }
        return teamsByNormalizedName.containsKey(normalize(teamName));
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
        return sessionMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
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

    public boolean isPairedMandatorySessions(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        return attendance != null && attendance.pairedMandatorySessions();
    }

    /**
     * Indicates whether the team has cancelled tutorial sessions in attendance data.
     *
     * @param teamName the team name to look up
     * @return true when any session attendance value is null
     */
    public boolean hasCancelledSessionWarning(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        if (attendance == null) {
            return false;
        }
        return hasNullAttendanceValue(attendance.student1Attendance())
                || hasNullAttendanceValue(attendance.student2Attendance());
    }

    /**
     * Checks if any attendance data has been uploaded.
     *
     * @return true if attendance data exists, false otherwise
     */
    public boolean hasAttendanceData() {
        return !teamsByNormalizedName.isEmpty();
    }

    /**
     * Clears all attendance data.
     * Should be called when clearing analysis data to ensure a fresh start.
     */
    public void clear() {
        teamsByNormalizedName.clear();
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

    private boolean hasNullAttendanceValue(Map<OffsetDateTime, Boolean> attendanceMap) {
        return attendanceMap != null
                && attendanceMap.values().stream().anyMatch(value -> value == null);
    }
}

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
        return getTeamAttendance(teamName, null);
    }

    /**
     * Retrieves the attendance information for a specific team, trying {@code teamName} first
     * then {@code shortNameFallback} if not found. Use when Excel may list teams by short name.
     *
     * @param teamName         the primary name of the team (e.g. full name)
     * @param shortNameFallback the short name to try if lookup by teamName fails
     * @return the team attendance DTO or null if not found
     */
    public TeamAttendanceDTO getTeamAttendance(String teamName, String shortNameFallback) {
        if (teamName != null) {
            TeamAttendanceDTO att = teamsByNormalizedName.get(normalize(teamName));
            if (att != null) {
                return att;
            }
        }
        if (shortNameFallback != null) {
            return teamsByNormalizedName.get(normalize(shortNameFallback));
        }
        return null;
    }

    /**
     * Returns the key under which attendance was found (teamName or shortNameFallback), or teamName if not found.
     * Use with {@link #getTeamAttendance(String, String)} to call {@link #getPairedSessions(String)}
     * or {@link #getClassDates(String)} with the resolved key.
     *
     * @param teamName         the primary name of the team
     * @param shortNameFallback the short name fallback
     * @return the resolved key, or teamName if neither matched
     */
    public String getResolvedTeamName(String teamName, String shortNameFallback) {
        if (teamName != null && teamsByNormalizedName.containsKey(normalize(teamName))) {
            return teamName;
        }
        if (shortNameFallback != null && teamsByNormalizedName.containsKey(normalize(shortNameFallback))) {
            return shortNameFallback;
        }
        return teamName;
    }

    /**
     * Checks if attendance data exists for a specific team.
     *
     * @param teamName the team name to look up
     * @return true if the team exists in uploaded attendance data, false otherwise
     */
    public boolean hasTeamAttendance(String teamName) {
        return hasTeamAttendance(teamName, null);
    }

    /**
     * Checks if attendance data exists for a team, trying {@code teamName} then {@code shortNameFallback}.
     *
     * @param teamName         the primary team name
     * @param shortNameFallback the short name to try if teamName is not found
     * @return true if the team exists in uploaded attendance data, false otherwise
     */
    public boolean hasTeamAttendance(String teamName, String shortNameFallback) {
        return getTeamAttendance(teamName, shortNameFallback) != null;
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
        return isPairedMandatorySessions(teamName, null);
    }

    /**
     * Same as {@link #isPairedMandatorySessions(String)} with shortName fallback.
     */
    public boolean isPairedMandatorySessions(String teamName, String shortNameFallback) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortNameFallback);
        return attendance != null && attendance.pairedMandatorySessions();
    }

    /**
     * Indicates whether the team has cancelled tutorial sessions in attendance data.
     *
     * @param teamName the team name to look up
     * @return true when any session attendance value is null
     */
    public boolean hasCancelledSessionWarning(String teamName) {
        return hasCancelledSessionWarning(teamName, null);
    }

    /**
     * Same as {@link #hasCancelledSessionWarning(String)} with shortName fallback.
     */
    public boolean hasCancelledSessionWarning(String teamName, String shortNameFallback) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortNameFallback);
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

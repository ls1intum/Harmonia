package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import static de.tum.cit.aet.dataProcessing.util.AttendanceUtils.normalize;
import static de.tum.cit.aet.dataProcessing.util.AttendanceUtils.normalizeForFuzzyMatch;
import static de.tum.cit.aet.dataProcessing.util.AttendanceUtils.levenshteinDistance;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TeamScheduleService {

    private final Map<String, TeamAttendanceDTO> teamsByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, String> fuzzyMatchCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> fuzzyMatchDistances = new ConcurrentHashMap<>();
    private static final int FUZZY_MATCH_THRESHOLD = 3;  // Levenshtein distance threshold

    /**
     * Updates the team schedule with the provided schedule data.
     *
     * @param schedule the teams schedule DTO
     */
    public void update(TeamsScheduleDTO schedule) {
        teamsByNormalizedName.clear();
        fuzzyMatchCache.clear();
        fuzzyMatchDistances.clear();
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
     * First tries exact match on normalized names, then falls back to fuzzy matching.
     *
     * @param teamName the name of the team
     * @return the team attendance DTO or null if not found
     */
    public TeamAttendanceDTO getTeamAttendance(String teamName) {
        if (teamName == null) {
            return null;
        }

        // Try exact match first
        String normalized = normalize(teamName);
        TeamAttendanceDTO exact = teamsByNormalizedName.get(normalized);
        if (exact != null) {
            return exact;
        }

        // Fall back to fuzzy matching
        return findTeamByFuzzyMatch(teamName);
    }

    /**
     * Finds a team using fuzzy matching when exact match fails.
     * Uses Levenshtein distance on normalized team names.
     *
     * @param teamName the name to match
     * @return the best matching team attendance, or null if no good match found
     */
    private TeamAttendanceDTO findTeamByFuzzyMatch(String teamName) {
        String fuzzyNormalized = normalizeForFuzzyMatch(teamName);

        // Check cache first
        if (fuzzyMatchCache.containsKey(fuzzyNormalized)) {
            String cachedKey = fuzzyMatchCache.get(fuzzyNormalized);
            return teamsByNormalizedName.get(cachedKey);
        }

        String bestMatchKey = null;
        int bestDistance = FUZZY_MATCH_THRESHOLD;

        // Find best match among all stored teams
        for (String storedKey : teamsByNormalizedName.keySet()) {
            String storedFuzzy = normalizeForFuzzyMatch(storedKey);
            int distance = levenshteinDistance(fuzzyNormalized, storedFuzzy);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatchKey = storedKey;
            }
        }

        if (bestMatchKey != null) {
            final String finalKey = bestMatchKey;
            log.warn("⚠️ FUZZY MATCH: Team '{}' not found exactly, matched to '{}' (distance: {})",
                    teamName, teamsByNormalizedName.keySet().stream()
                            .filter(k -> k.equals(finalKey))
                            .findFirst()
                            .orElse(finalKey), bestDistance);
            fuzzyMatchCache.put(fuzzyNormalized, bestMatchKey);
            fuzzyMatchDistances.put(fuzzyNormalized, bestDistance);
            return teamsByNormalizedName.get(bestMatchKey);
        }

        log.warn("❌ NO MATCH: Team '{}' could not be found even with fuzzy matching", teamName);
        return null;
    }

    /**
     * Checks if attendance data exists for a specific team.
     * Uses exact match first, then falls back to fuzzy matching.
     *
     * @param teamName the team name to look up
     * @return true if the team exists in uploaded attendance data, false otherwise
     */
    public boolean hasTeamAttendance(String teamName) {
        if (teamName == null) {
            return false;
        }
        return getTeamAttendance(teamName) != null;
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
     * Checks if a team was found via fuzzy matching (not exact match).
     *
     * @param teamName the team name to check
     * @return true if team was found via fuzzy matching, false if exact match or not found
     */
    public boolean wasFuzzyMatched(String teamName) {
        if (teamName == null) {
            return false;
        }
        String fuzzyNormalized = normalizeForFuzzyMatch(teamName);
        return fuzzyMatchDistances.containsKey(fuzzyNormalized);
    }

    /**
     * Gets the fuzzy match distance for a team (if it was fuzzy matched).
     *
     * @param teamName the team name
     * @return the Levenshtein distance, or -1 if not fuzzy matched
     */
    public int getFuzzyMatchDistance(String teamName) {
        if (teamName == null) {
            return -1;
        }
        String fuzzyNormalized = normalizeForFuzzyMatch(teamName);
        return fuzzyMatchDistances.getOrDefault(fuzzyNormalized, -1);
    }

    /**
     * Clears all attendance data.
     * Should be called when clearing analysis data to ensure a fresh start.
     */
    public void clear() {
        teamsByNormalizedName.clear();
        fuzzyMatchCache.clear();
        fuzzyMatchDistances.clear();
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

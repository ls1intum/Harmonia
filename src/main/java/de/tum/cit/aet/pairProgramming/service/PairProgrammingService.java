package de.tum.cit.aet.pairProgramming.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.service.cqi.PairProgrammingCalculator;
import de.tum.cit.aet.core.config.AttendanceConfiguration;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.pairProgramming.dto.TeamAttendanceDTO;
import de.tum.cit.aet.pairProgramming.dto.TeamsScheduleDTO;
import de.tum.cit.aet.pairProgramming.enums.PairProgrammingStatus;
import de.tum.cit.aet.repositoryProcessing.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.ArtemisClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.tum.cit.aet.pairProgramming.util.AttendanceUtils.normalize;
import static de.tum.cit.aet.pairProgramming.util.AttendanceUtils.normalizeForFuzzyMatch;
import static de.tum.cit.aet.pairProgramming.util.AttendanceUtils.levenshteinDistance;

/**
 * Central service for pair programming and attendance management.
 * Handles Excel attendance parsing, in-memory schedule storage,
 * team attendance lookups, and async recomputation of pair programming metrics.
 */
@Service
@Slf4j
public class PairProgrammingService {

    private final ArtemisClientService artemisClientService;
    private final AttendanceConfiguration attendanceConfiguration;
    private final PairProgrammingCalculator pairProgrammingCalculator;

    private final Map<String, TeamAttendanceDTO> teamsByNormalizedName = new ConcurrentHashMap<>();

    public PairProgrammingService(ArtemisClientService artemisClientService,
                                  AttendanceConfiguration attendanceConfiguration,
                                  PairProgrammingCalculator pairProgrammingCalculator) {
        this.artemisClientService = artemisClientService;
        this.attendanceConfiguration = attendanceConfiguration;
        this.pairProgrammingCalculator = pairProgrammingCalculator;
    }

    // ---- Attendance parsing ----

    /**
     * Parses attendance data from an Excel file and updates the in-memory schedule.
     *
     * <p>Steps:
     * 1) Fetches tutorial group sessions and submission deadline from Artemis.
     * 2) Iterates over each sheet and matches it to a tutorial group.
     * 3) Parses team attendance rows and stores the result.
     *
     * @param file        the Excel file to parse
     * @param credentials the Artemis credentials
     * @param courseId    the course ID
     * @param exerciseId  the exercise ID
     * @return the parsed teams schedule
     */
    public TeamsScheduleDTO parseAttendance(MultipartFile file, ArtemisCredentials credentials,
                                            Long courseId, Long exerciseId) {
        // 1) Fetch tutorial group sessions and submission deadline from Artemis
        Map<String, List<TutorialGroupSessionDTO>> sessionsByGroup = artemisClientService
                .fetchTutorialGroupSessions(credentials.serverUrl(), credentials.jwtToken(), courseId);

        OffsetDateTime submissionDeadline = artemisClientService.fetchSubmissionDeadline(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);

        Map<String, List<TutorialGroupSessionDTO>> normalizedSessions = new LinkedHashMap<>();
        sessionsByGroup.forEach((name, sessions) -> normalizedSessions.put(normalize(name), sessions));

        // 2) Iterate over each sheet and parse attendance
        Map<String, TeamAttendanceDTO> teams = new LinkedHashMap<>();
        Set<String> normalizedTeamNames = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                List<TutorialGroupSessionDTO> sessionInfos = normalizedSessions.getOrDefault(normalize(sheetName), List.of());

                if (sessionInfos.isEmpty()) {
                    log.warn("No tutorial group sessions found for sheet '{}'", sheetName);
                }

                List<TutorialGroupSessionDTO> sortedSessions = filterAndSortSessions(sessionInfos, submissionDeadline);

                Map<String, TeamAttendanceDTO> parsedTeams = parseSheet(sheet, sortedSessions, formatter);
                for (Map.Entry<String, TeamAttendanceDTO> entry : parsedTeams.entrySet()) {
                    String teamName = entry.getKey();
                    String normalizedName = normalize(teamName);
                    if (normalizedTeamNames.contains(normalizedName)) {
                        log.warn("Duplicate team name '{}' found in sheet '{}'; keeping first entry", teamName, sheetName);
                        continue;
                    }
                    normalizedTeamNames.add(normalizedName);
                    teams.put(teamName, entry.getValue());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse attendance file", e);
        }

        // 3) Store the result in-memory
        TeamsScheduleDTO schedule = new TeamsScheduleDTO(teams);
        updateSchedule(schedule);
        return schedule;
    }

    // ---- Schedule storage ----

    /**
     * Replaces the in-memory schedule with new data.
     *
     * @param schedule the teams schedule DTO
     */
    public void updateSchedule(TeamsScheduleDTO schedule) {
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
     * Resolves the key used in the attendance map: tries normalized team name first,
     * then normalized short name, then fuzzy matching. Used so Excel rows keyed by short name still match
     * Artemis teams that are displayed by full name, and teams with slightly different names still match.
     *
     * @param teamName  the full team name
     * @param shortName the short team name (optional fallback)
     * @return the key present in {@code teamsByNormalizedName}, or {@code null} if not found
     */
    private String resolveAttendanceKey(String teamName, String shortName) {
        // 1) Try exact normalized match with full team name
        if (teamName != null) {
            String key = normalize(teamName);
            if (teamsByNormalizedName.containsKey(key)) {
                return key;
            }
        }

        // 2) Try exact normalized match with short team name
        if (shortName != null && !shortName.equals(teamName)) {
            String key = normalize(shortName);
            if (teamsByNormalizedName.containsKey(key)) {
                return key;
            }
        }

        // 3) Try fuzzy matching if exact match fails
        String fuzzyMatchedKey = resolveFuzzyAttendanceKey(teamName, shortName);
        if (fuzzyMatchedKey != null) {
            return fuzzyMatchedKey;
        }

        return teamName != null ? normalize(teamName) : null;
    }

    /**
     * Resolves team name using fuzzy matching when exact normalized match fails.
     * Uses Levenshtein distance to find the best match above a similarity threshold.
     *
     * @param teamName  the full team name
     * @param shortName the short team name (optional)
     * @return the best fuzzy-matched key, or {@code null} if no match above threshold
     */
    private String resolveFuzzyAttendanceKey(String teamName, String shortName) {
        if (teamsByNormalizedName.isEmpty()) {
            return null;
        }

        String fuzzyTeamName = teamName != null ? normalizeForFuzzyMatch(teamName) : null;
        String fuzzyShortName = (shortName != null && !shortName.equals(teamName))
                ? normalizeForFuzzyMatch(shortName)
                : null;

        int bestDistance = Integer.MAX_VALUE;
        String bestMatchKey = null;

        for (String storedKey : teamsByNormalizedName.keySet()) {
            String storedNormalized = normalizeForFuzzyMatch(storedKey);

            // Try distance with full team name
            if (fuzzyTeamName != null && !fuzzyTeamName.isEmpty()) {
                int distance = levenshteinDistance(fuzzyTeamName, storedNormalized);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatchKey = storedKey;
                }
            }

            // Try distance with short team name as well
            if (fuzzyShortName != null && !fuzzyShortName.isEmpty()) {
                int distance = levenshteinDistance(fuzzyShortName, storedNormalized);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatchKey = storedKey;
                }
            }
        }

        // Only accept match if similarity is above threshold (80%)
        // Calculate based on the longer of the two strings being compared
        if (bestMatchKey != null) {
            String storedNormalized = normalizeForFuzzyMatch(bestMatchKey);
            int maxLength = Math.max(
                    fuzzyTeamName != null ? fuzzyTeamName.length() : 0,
                    storedNormalized.length()
            );

            if (maxLength > 0) {
                double similarity = 1.0 - (double) bestDistance / maxLength;
                if (similarity >= 0.80) {
                    log.debug("Fuzzy matched team '{}' to '{}' with similarity {}",
                            teamName, storedNormalized, String.format("%.2f", similarity * 100));
                    return bestMatchKey;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves the attendance information for a specific team.
     * Tries full name first, then short name as fallback.
     *
     * @param teamName  the name of the team
     * @param shortName the short name of the team (optional fallback when Excel uses short names)
     * @return the team attendance DTO or {@code null} if not found
     */
    public TeamAttendanceDTO getTeamAttendance(String teamName, String shortName) {
        String key = resolveAttendanceKey(teamName, shortName);
        return key != null ? teamsByNormalizedName.get(key) : null;
    }

    /**
     * Retrieves the attendance information for a specific team by name only.
     *
     * @param teamName the name of the team
     * @return the team attendance DTO or {@code null} if not found
     */
    public TeamAttendanceDTO getTeamAttendance(String teamName) {
        return getTeamAttendance(teamName, null);
    }

    /**
     * Checks if attendance data exists for a specific team.
     * Tries full name first, then short name as fallback.
     *
     * @param teamName  the team name to look up
     * @param shortName the short team name (optional fallback)
     * @return {@code true} if the team exists in uploaded attendance data
     */
    public boolean hasTeamAttendance(String teamName, String shortName) {
        String key = resolveAttendanceKey(teamName, shortName);
        return key != null && teamsByNormalizedName.containsKey(key);
    }

    /**
     * Checks if attendance data exists for a specific team by name only.
     *
     * @param teamName the team name to look up
     * @return {@code true} if the team exists in uploaded attendance data
     */
    public boolean hasTeamAttendance(String teamName) {
        return hasTeamAttendance(teamName, null);
    }

    /**
     * Retrieves the class dates (non-null attendance entries) for a team.
     * Uses short name as fallback when full name is not found.
     *
     * @param teamName  the name of the team
     * @param shortName the short team name (optional fallback)
     * @return a set of class dates
     */
    public Set<OffsetDateTime> getClassDates(String teamName, String shortName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortName);
        if (attendance == null) {
            return Set.of();
        }
        Map<OffsetDateTime, Boolean> sessionMap = firstNonEmpty(
                attendance.student1Attendance(), attendance.student2Attendance());
        if (sessionMap.isEmpty()) {
            return Set.of();
        }
        return sessionMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the class dates for a team by name only.
     *
     * @param teamName the name of the team
     * @return a set of class dates
     */
    public Set<OffsetDateTime> getClassDates(String teamName) {
        return getClassDates(teamName, null);
    }

    /**
     * Retrieves the sessions where both students were present.
     * Uses short name as fallback when full name is not found.
     *
     * @param teamName  the name of the team
     * @param shortName the short team name (optional fallback)
     * @return a set of paired session dates
     */
    public Set<OffsetDateTime> getPairedSessions(String teamName, String shortName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortName);
        if (attendance == null || attendance.pairedSessions() == null) {
            return Set.of();
        }
        return new HashSet<>(attendance.pairedSessions());
    }

    /**
     * Retrieves the paired sessions for a team by name only.
     *
     * @param teamName the name of the team
     * @return a set of paired session dates
     */
    public Set<OffsetDateTime> getPairedSessions(String teamName) {
        return getPairedSessions(teamName, null);
    }

    /**
     * Checks whether the team met the mandatory paired session threshold.
     * Uses short name as fallback when full name is not found.
     *
     * @param teamName  the team name
     * @param shortName the short team name (optional fallback)
     * @return {@code true} if mandatory sessions are fulfilled
     */
    public boolean isPairedMandatorySessions(String teamName, String shortName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortName);
        return attendance != null && attendance.pairedMandatorySessions();
    }

    /**
     * Checks whether the team met the mandatory paired session threshold by name only.
     *
     * @param teamName the team name
     * @return {@code true} if mandatory sessions are fulfilled
     */
    public boolean isPairedMandatorySessions(String teamName) {
        return isPairedMandatorySessions(teamName, null);
    }

    /**
     * Indicates whether the team has cancelled tutorial sessions in attendance data.
     * Uses short name as fallback when full name is not found.
     *
     * @param teamName  the team name to look up
     * @param shortName the short team name (optional fallback)
     * @return {@code true} when any session attendance value is {@code null}
     */
    public boolean hasCancelledSessionWarning(String teamName, String shortName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName, shortName);
        if (attendance == null) {
            return false;
        }
        return hasNullAttendanceValue(attendance.student1Attendance())
                || hasNullAttendanceValue(attendance.student2Attendance());
    }

    /**
     * Indicates whether the team has cancelled tutorial sessions (by name only).
     *
     * @param teamName the team name to look up
     * @return {@code true} when any session attendance value is {@code null}
     */
    public boolean hasCancelledSessionWarning(String teamName) {
        return hasCancelledSessionWarning(teamName, null);
    }

    /**
     * Checks if any attendance data has been uploaded.
     *
     * @return {@code true} if attendance data exists
     */
    public boolean hasAttendanceData() {
        return !teamsByNormalizedName.isEmpty();
    }

    /**
     * Clears all in-memory attendance data.
     */
    public void clear() {
        teamsByNormalizedName.clear();
    }

    // ---- Pair programming status & score ----

    /**
     * Resolves the pair programming status from attendance state.
     *
     * @param teamName  the full team name
     * @param shortName the short team name (optional fallback)
     * @return the pair programming status
     */
    public PairProgrammingStatus getPairProgrammingStatus(String teamName, String shortName) {
        boolean hasAttendance = hasTeamAttendance(teamName, shortName);
        boolean hasCancelledWarning = hasCancelledSessionWarning(teamName, shortName);
        boolean pairedMandatory = isPairedMandatorySessions(teamName, shortName);
        return PairProgrammingStatus.fromAttendanceState(hasAttendance, hasCancelledWarning, pairedMandatory);
    }

    /**
     * Calculates the pair programming score for a team (teams of 2 only).
     *
     * @param teamName  the full team name
     * @param shortName the short team name (optional fallback)
     * @param chunks    the commit chunks
     * @param teamSize  the number of team members
     * @return score 0-100, or {@code null} if not applicable
     */
    public Double calculateScore(String teamName, String shortName,
                                 List<CommitChunkDTO> chunks, int teamSize) {
        if (teamName == null || teamSize != 2) {
            return null;
        }
        try {
            boolean hasAttendance = hasTeamAttendance(teamName, shortName);
            boolean hasCancelledWarning = hasCancelledSessionWarning(teamName, shortName);

            if (!hasAttendance) {
                return null;
            }

            Set<OffsetDateTime> pairedSessions = getPairedSessions(teamName, shortName);
            Set<OffsetDateTime> allSessions = getClassDates(teamName, shortName);

            if (hasCancelledWarning) {
                log.warn("Team '{}' has cancelled tutorial sessions; pair programming status set to WARNING", teamName);
                return null;
            }

            if (!pairedSessions.isEmpty() && !allSessions.isEmpty()) {
                return pairProgrammingCalculator.calculateFromChunks(
                        pairedSessions, allSessions, chunks, teamSize);
            }

            log.warn("Team '{}' found in attendance but has no {} sessions; treating as failed (score 0)",
                    teamName, allSessions.isEmpty() ? "mapped tutorial" : "paired");
            return 0.0;
        } catch (Exception e) {
            log.error("Failed to calculate pair programming score for team {}: {}", teamName, e.getMessage(), e);
            return null;
        }
    }

    // ---- Private helpers ----

    private List<TutorialGroupSessionDTO> filterAndSortSessions(List<TutorialGroupSessionDTO> sessions,
                                                                 OffsetDateTime deadline) {
        long count = sessions.stream()
                .filter(s -> s.start().isBefore(deadline))
                .count();

        return sessions.stream()
                .filter(s -> s.start().isBefore(deadline))
                .sorted(Comparator.comparing(TutorialGroupSessionDTO::start))
                .skip(Math.max(0, count - attendanceConfiguration.getNumberProgrammingSessions()))
                .toList();
    }

    private Map<String, TeamAttendanceDTO> parseSheet(Sheet sheet,
                                                       List<TutorialGroupSessionDTO> sessionInfos,
                                                       DataFormatter formatter) {
        Map<String, TeamAttendanceDTO> teams = new LinkedHashMap<>();
        int rowIndex = attendanceConfiguration.getStartRowIndex();
        int teamNameColumn = attendanceConfiguration.getTeamNameColumn();
        int neighboringColumn = teamNameColumn + 1;

        while (true) {
            Row row = sheet.getRow(rowIndex);
            String teamName = getCellString(row, teamNameColumn, formatter);
            if (teamName == null || teamName.isBlank()) {
                String neighboringValue = getCellString(row, neighboringColumn, formatter);
                if (neighboringValue == null || neighboringValue.isBlank()) {
                    break;
                }
                rowIndex += attendanceConfiguration.getRowStep();
                continue;
            }

            Map<OffsetDateTime, Boolean> student1Attendance = new LinkedHashMap<>();
            Map<OffsetDateTime, Boolean> student2Attendance = new LinkedHashMap<>();

            for (int i = 0; i < sessionInfos.size(); i++) {
                TutorialGroupSessionDTO sessionInfo = sessionInfos.get(i);
                OffsetDateTime sessionTime = sessionInfo.start();

                if (sessionInfo.cancelled()) {
                    student1Attendance.put(sessionTime, null);
                    student2Attendance.put(sessionTime, null);
                    continue;
                }

                Boolean student1 = getCellBoolean(row, attendanceConfiguration.getStudent1Columns()[i], formatter);
                Boolean student2 = getCellBoolean(row, attendanceConfiguration.getStudent2Columns()[i], formatter);
                student1Attendance.put(sessionTime, Boolean.TRUE.equals(student1));
                student2Attendance.put(sessionTime, Boolean.TRUE.equals(student2));
            }

            List<OffsetDateTime> pairedSessions = student1Attendance.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue())
                            && Boolean.TRUE.equals(student2Attendance.get(entry.getKey())))
                    .map(Map.Entry::getKey)
                    .toList();

            boolean pairedMandatorySessions = pairedSessions.size() >= attendanceConfiguration.getMandatoryProgrammingSessions();

            teams.put(teamName.trim(), new TeamAttendanceDTO(
                    student1Attendance, student2Attendance, pairedMandatorySessions, pairedSessions));

            rowIndex += attendanceConfiguration.getRowStep();
        }

        return teams;
    }

    private String getCellString(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return value != null ? value.trim() : null;
    }

    private Boolean getCellBoolean(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> cell.getNumericCellValue() > 0;
            default -> parseBooleanString(formatter.formatCellValue(cell));
        };
    }

    private Boolean parseBooleanString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.equals("true")
                || normalized.equals("t")
                || normalized.equals("yes")
                || normalized.equals("y")
                || normalized.equals("1");
    }

    private Map<OffsetDateTime, Boolean> firstNonEmpty(Map<OffsetDateTime, Boolean> first,
                                                        Map<OffsetDateTime, Boolean> second) {
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

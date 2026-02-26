package de.tum.cit.aet.pairProgramming.service;

import de.tum.cit.aet.core.config.AttendanceConfiguration;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.pairProgramming.dto.TeamAttendanceDTO;
import de.tum.cit.aet.pairProgramming.dto.TeamsScheduleDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.ArtemisClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
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
    private final RequestService requestService;

    private final Map<String, TeamAttendanceDTO> teamsByNormalizedName = new ConcurrentHashMap<>();

    public PairProgrammingService(ArtemisClientService artemisClientService,
                                  AttendanceConfiguration attendanceConfiguration,
                                  @Lazy RequestService requestService) {
        this.artemisClientService = artemisClientService;
        this.attendanceConfiguration = attendanceConfiguration;
        this.requestService = requestService;
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
     * Retrieves the attendance information for a specific team.
     *
     * @param teamName the name of the team
     * @return the team attendance DTO or {@code null} if not found
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
     * @return {@code true} if the team exists in uploaded attendance data
     */
    public boolean hasTeamAttendance(String teamName) {
        if (teamName == null) {
            return false;
        }
        return teamsByNormalizedName.containsKey(normalize(teamName));
    }

    /**
     * Retrieves the class dates (non-null attendance entries) for a team.
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
     * Retrieves the sessions where both students were present.
     *
     * @param teamName the name of the team
     * @return a set of paired session dates
     */
    public Set<OffsetDateTime> getPairedSessions(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        if (attendance == null || attendance.pairedSessions() == null) {
            return Set.of();
        }
        return new HashSet<>(attendance.pairedSessions());
    }

    /**
     * Checks whether the team met the mandatory paired session threshold.
     *
     * @param teamName the team name
     * @return {@code true} if mandatory sessions are fulfilled
     */
    public boolean isPairedMandatorySessions(String teamName) {
        TeamAttendanceDTO attendance = getTeamAttendance(teamName);
        return attendance != null && attendance.pairedMandatorySessions();
    }

    /**
     * Indicates whether the team has cancelled tutorial sessions in attendance data.
     *
     * @param teamName the team name to look up
     * @return {@code true} when any session attendance value is {@code null}
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

    // ---- Async recomputation ----

    /**
     * Recomputes pair programming metrics for an exercise asynchronously.
     *
     * @param exerciseId the exercise ID
     */
    @Async("attendanceTaskExecutor")
    public void recomputeForExerciseAsync(Long exerciseId) {
        try {
            int updatedTeams = requestService.recomputePairProgrammingForExercise(exerciseId);
            log.info("Async recomputed pair programming metrics for {} teams (exercise={})",
                    updatedTeams, exerciseId);
        } catch (Exception e) {
            log.error("Async pair programming recomputation failed for exercise {}", exerciseId, e);
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

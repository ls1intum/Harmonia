package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.core.config.AttendanceConfiguration;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.dto.TeamAttendanceDTO;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AttendanceService {
    /**
     * Processes the Excel file information and combines it with the tutorial group
     * timeslots fetched via the Artemis API
     */
    private final ArtemisClientService artemisClientService;
    private final TeamScheduleService teamScheduleService;
    private final AttendanceConfiguration attendanceConfiguration;

    /**
     * Constructs the AttendanceService with required dependencies.
     *
     * @param artemisClientService the Artemis client service
     * @param teamScheduleService the team schedule service
     * @param attendanceConfiguration the attendance configuration
     */
    public AttendanceService(ArtemisClientService artemisClientService, TeamScheduleService teamScheduleService, AttendanceConfiguration attendanceConfiguration) {
        this.artemisClientService = artemisClientService;
        this.teamScheduleService = teamScheduleService;
        this.attendanceConfiguration = attendanceConfiguration;
    }

    /**
     * Parses attendance data from an Excel file.
     *
     * @param file the Excel file to parse
     * @param credentials the Artemis credentials
     * @param courseId the course ID
     * @param exerciseId the exercise ID
     * @return the teams schedule DTO
     */
    public TeamsScheduleDTO parseAttendance(
            MultipartFile file,
            ArtemisCredentials credentials,
            Long courseId,
            Long exerciseId
    ) {
        Map<String, List<OffsetDateTime>> sessionsByGroup = artemisClientService
                .fetchTutorialGroupSessionStartTimes(credentials.serverUrl(), credentials.jwtToken(), courseId);

        OffsetDateTime submissionDeadline = artemisClientService.fetchSubmissionDeadline(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);

        Map<String, List<OffsetDateTime>> normalizedSessions = new LinkedHashMap<>();
        sessionsByGroup.forEach((name, sessions) -> normalizedSessions.put(normalizeName(name), sessions));

        Map<String, TeamAttendanceDTO> teams = new LinkedHashMap<>();
        Set<String> normalizedTeamNames = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                List<OffsetDateTime> sessionTimes = normalizedSessions.getOrDefault(normalizeName(sheetName), List.of());

                if (sessionTimes.isEmpty()) {
                    log.warn("No tutorial group sessions found for sheet '{}'", sheetName);
                }



                long count = sessionTimes.stream()
                        .filter(session -> session.isBefore(submissionDeadline))
                        .count();

                List<OffsetDateTime> sortedSessions = sessionTimes.stream()
                        .filter(session -> session.isBefore(submissionDeadline))
                        .sorted()
                        .skip(Math.max(0, count - attendanceConfiguration.getNumberProgrammingSessions()))
                        .toList();

                Map<String, TeamAttendanceDTO> parsedTeams = parseSheet(sheet, sortedSessions, formatter);
                for (Map.Entry<String, TeamAttendanceDTO> entry : parsedTeams.entrySet()) {
                    String teamName = entry.getKey();
                    TeamAttendanceDTO teamAttendance = entry.getValue();
                    String normalizedName = normalizeName(teamName);
                    if (normalizedTeamNames.contains(normalizedName)) {
                        log.warn("Duplicate team name '{}' found in sheet '{}'; keeping first entry",
                                teamName, sheetName);
                        continue;
                    }
                    normalizedTeamNames.add(normalizedName);
                    teams.put(teamName, teamAttendance);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse attendance file", e);
        }

        TeamsScheduleDTO schedule = new TeamsScheduleDTO(teams);
        teamScheduleService.update(schedule);
        return schedule;
    }

    private Map<String, TeamAttendanceDTO> parseSheet(Sheet sheet, List<OffsetDateTime> sessionTimes, DataFormatter formatter) {
        Map<String, TeamAttendanceDTO> teams = new LinkedHashMap<>();
        int rowIndex = attendanceConfiguration.getStartRowIndex();

        while (true) {
            Row row = sheet.getRow(rowIndex);
            String teamName = getCellString(row, attendanceConfiguration.getTeamNameColumn(), formatter);
            if (teamName == null || teamName.isBlank()) {
                break;
            }

            Map<OffsetDateTime, Boolean> student1Attendance = new LinkedHashMap<>();
            Map<OffsetDateTime, Boolean> student2Attendance = new LinkedHashMap<>();

            for (int i = 0; i < sessionTimes.size(); i++) {
                OffsetDateTime sessionTime = sessionTimes.get(i);
                Boolean student1 = getCellBoolean(row, attendanceConfiguration.getStudent1Columns()[i], formatter);
                Boolean student2 = getCellBoolean(row, attendanceConfiguration.getStudent2Columns()[i], formatter);
                student1Attendance.put(sessionTime, student1);
                student2Attendance.put(sessionTime, student2);
            }

            List<OffsetDateTime> pairedSessions = student1Attendance.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue())
                            && Boolean.TRUE.equals(student2Attendance.get(entry.getKey())))
                    .map(Map.Entry::getKey)
                    .toList();

            boolean pairedAtLeastTwoOfThree = pairedSessions.size() >= 2;

            teams.put(teamName.trim(), new TeamAttendanceDTO(
                    student1Attendance,
                    student2Attendance,
                    pairedAtLeastTwoOfThree,
                    pairedSessions
            ));

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
            case STRING -> parseBooleanString(formatter.formatCellValue(cell));
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

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}

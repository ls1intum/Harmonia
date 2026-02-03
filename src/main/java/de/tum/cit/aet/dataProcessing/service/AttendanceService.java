package de.tum.cit.aet.dataProcessing.service;

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

    private static final int START_ROW_INDEX = 4; // row 5 in Excel (0-based index)
    private static final int ROW_STEP = 3;
    private static final int TEAM_NAME_COLUMN = 0; // column A
    private static final int[] STUDENT1_COLUMNS = new int[]{4, 8, 12}; // E, I, M
    private static final int[] STUDENT2_COLUMNS = new int[]{5, 9, 13}; // F, J, N

    private final ArtemisClientService artemisClientService;
    private final TeamScheduleService teamScheduleService;

    public AttendanceService(ArtemisClientService artemisClientService, TeamScheduleService teamScheduleService) {
        this.artemisClientService = artemisClientService;
        this.teamScheduleService = teamScheduleService;
    }

    public TeamsScheduleDTO parseAttendance(
            MultipartFile file,
            ArtemisCredentials credentials,
            Long courseId
    ) {
        Map<String, List<OffsetDateTime>> sessionsByGroup = artemisClientService
                .fetchTutorialGroupSessionStartTimes(credentials.serverUrl(), credentials.jwtToken(), courseId);

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

                OffsetDateTime submissionDeadline = OffsetDateTime.parse("2026-01-28T13:00:00+01:00");

                long count = sessionTimes.stream()
                        .filter(session -> session.isBefore(submissionDeadline))
                        .count();

                List<OffsetDateTime> sortedSessions = sessionTimes.stream()
                        .filter(session -> session.isBefore(submissionDeadline))
                        .sorted()
                        .skip(Math.max(0, count - 3))
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
        int rowIndex = START_ROW_INDEX;

        while (true) {
            Row row = sheet.getRow(rowIndex);
            String teamName = getCellString(row, TEAM_NAME_COLUMN, formatter);
            if (teamName == null || teamName.isBlank()) {
                break;
            }

            Map<OffsetDateTime, Boolean> student1Attendance = new LinkedHashMap<>();
            Map<OffsetDateTime, Boolean> student2Attendance = new LinkedHashMap<>();

            for (int i = 0; i < sessionTimes.size(); i++) {
                OffsetDateTime sessionTime = sessionTimes.get(i);
                Boolean student1 = getCellBoolean(row, STUDENT1_COLUMNS[i], formatter);
                Boolean student2 = getCellBoolean(row, STUDENT2_COLUMNS[i], formatter);
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

            rowIndex += ROW_STEP;
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

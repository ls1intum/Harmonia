package de.tum.cit.aet.analysis.service.cqi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage team class schedules for pair programming detection.
 * 
 * Teams are scheduled for tutorials on specific days (e.g., Mondays 10-12, Wednesdays 14-16).
 * This service maps team names to their scheduled class dates.
 * 
 * When commits occur on a team's scheduled class day, it's a strong signal of pair programming,
 * as students are physically present together during the tutorial.
 */
@Service
@Slf4j
public class TeamScheduleService {

    /**
     * Map of team name to set of class dates
     * Example: "Team4" -> {2026-01-13, 2026-01-20, 2026-01-27, ...}
     */
    private final Map<String, Set<LocalDate>> teamSchedules = new ConcurrentHashMap<>();
    
    /**
     * Map of team name to class day of week and time slot (for reference)
     * Example: "Team4" -> "Monday 10:00-12:00"
     */
    private final Map<String, String> teamDaySlots = new ConcurrentHashMap<>();

    /**
     * Get the set of class dates for a team
     * 
     * @param teamName The name of the team
     * @return Set of dates when this team has class, or empty set if not found
     */
    public Set<LocalDate> getClassDates(String teamName) {
        return teamSchedules.getOrDefault(teamName, Set.of());
    }

    /**
     * Get the class day and time slot for a team (for display/reference)
     * 
     * @param teamName The name of the team
     * @return String like "Monday 10:00-12:00" or null if not found
     */
    public String getClassDaySlot(String teamName) {
        return teamDaySlots.get(teamName);
    }

    /**
     * Register a team's class schedule
     * 
     * @param teamName The name of the team
     * @param classDates Set of dates when this team has class
     * @param daySlotDescription Optional description of class day/time (e.g., "Monday 10:00-12:00")
     */
    public void registerTeamSchedule(String teamName, Set<LocalDate> classDates, String daySlotDescription) {
        teamSchedules.put(teamName, new HashSet<>(classDates));
        if (daySlotDescription != null) {
            teamDaySlots.put(teamName, daySlotDescription);
        }
        log.info("Registered schedule for team {}: {} class dates, slot: {}", 
                teamName, classDates.size(), daySlotDescription);
    }

    /**
     * Register a team's class schedule with just one representative date and day of week
     * Useful when you have the day of week and want to infer all dates in a semester
     * 
     * @param teamName The name of the team
     * @param dayOfWeek Day of week (1=Monday, 2=Tuesday, ..., 7=Sunday)
     * @param timeSlot Optional time slot description (e.g., "10:00-12:00")
     * @param semesterStartDate Start of semester
     * @param semesterEndDate End of semester
     */
    public void registerTeamByDayOfWeek(String teamName, int dayOfWeek, String timeSlot, 
                                        LocalDate semesterStartDate, LocalDate semesterEndDate) {
        Set<LocalDate> classDates = new HashSet<>();
        LocalDate current = semesterStartDate;

        // Find all dates matching the day of week in the semester
        while (!current.isAfter(semesterEndDate)) {
            if (current.getDayOfWeek().getValue() == dayOfWeek) {
                classDates.add(current);
            }
            current = current.plusDays(1);
        }

        String dayName = getDayName(dayOfWeek);
        String description = dayName + (timeSlot != null ? " " + timeSlot : "");
        registerTeamSchedule(teamName, classDates, description);
    }

    /**
     * Load team schedules from a CSV format
     * Expected format per line: "TeamName,Monday,10:00-12:00" or "Team4,Monday,14:00-16:00"
     * 
     * This method expects you to also provide the semester dates to calculate all class dates
     * 
     * @param csvContent CSV content with team schedules
     * @param semesterStartDate Start of semester
     * @param semesterEndDate End of semester
     * @throws IllegalArgumentException if CSV format is invalid
     */
    public void loadFromCSV(String csvContent, LocalDate semesterStartDate, LocalDate semesterEndDate) {
        log.info("Loading team schedules from CSV");
        
        String[] lines = csvContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip empty lines and comments
            }

            String[] parts = line.split(",");
            if (parts.length < 2) {
                log.warn("Invalid CSV line, skipping: {}", line);
                continue;
            }

            String teamName = parts[0].trim();
            String dayOfWeekStr = parts[1].trim();
            String timeSlot = parts.length > 2 ? parts[2].trim() : null;

            try {
                int dayOfWeek = parseDayOfWeek(dayOfWeekStr);
                registerTeamByDayOfWeek(teamName, dayOfWeek, timeSlot, semesterStartDate, semesterEndDate);
            } catch (IllegalArgumentException e) {
                log.error("Failed to parse day of week '{}' for team {}: {}", dayOfWeekStr, teamName, e.getMessage());
            }
        }

        log.info("Loaded schedules for {} teams", teamSchedules.size());
    }

    /**
     * Load team schedules from Excel-like format (when you get the Excel file)
     * This is a placeholder that can be extended
     * 
     * @param excelFilePath Path to the Excel file
     * @param semesterStartDate Start of semester
     * @param semesterEndDate End of semester
     */
    public void loadFromExcel(String excelFilePath, LocalDate semesterStartDate, LocalDate semesterEndDate) {
        log.info("Loading team schedules from Excel: {}", excelFilePath);
        // TODO: Implement when Excel file is available
        // Will likely use Apache POI or similar library
        // Expected columns: Team Name, Day of Week, Time Slot
    }

    /**
     * Clear all registered schedules
     */
    public void clearAllSchedules() {
        teamSchedules.clear();
        teamDaySlots.clear();
        log.info("Cleared all team schedules");
    }

    /**
     * Get all registered teams
     */
    public Set<String> getAllTeams() {
        return new HashSet<>(teamSchedules.keySet());
    }

    /**
     * Check if a date is a class day for any team
     */
    public boolean isClassDay(LocalDate date) {
        return teamSchedules.values().stream()
                .anyMatch(dates -> dates.contains(date));
    }

    /**
     * Get all teams that have class on a specific date
     */
    public Set<String> getTeamsWithClassOn(LocalDate date) {
        Set<String> teams = new HashSet<>();
        teamSchedules.forEach((team, dates) -> {
            if (dates.contains(date)) {
                teams.add(team);
            }
        });
        return teams;
    }

    /**
     * Parse day of week string (e.g., "Monday", "Mon", "1")
     */
    private int parseDayOfWeek(String dayStr) {
        String lower = dayStr.toLowerCase();
        
        return switch (lower) {
            case "monday", "mon", "1" -> 1;
            case "tuesday", "tue", "2" -> 2;
            case "wednesday", "wed", "3" -> 3;
            case "thursday", "thu", "4" -> 4;
            case "friday", "fri", "5" -> 5;
            case "saturday", "sat", "6" -> 6;
            case "sunday", "sun", "7" -> 7;
            default -> throw new IllegalArgumentException("Unknown day of week: " + dayStr);
        };
    }

    /**
     * Get day name from day of week number
     */
    private String getDayName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            case 7 -> "Sunday";
            default -> "Unknown";
        };
    }
}

package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.AnomalyDetectionRequestDTO;
import de.tum.cit.aet.ai.dto.AnomalyFlag;
import de.tum.cit.aet.ai.dto.AnomalyReportDTO;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("!openapi")
public class AnomalyDetectorService {

    /**
     * Prompt template for anomaly detection.
     * Placeholders: assignmentStart, assignmentEnd, totalDays, commitCount, teamMembers, commitTimeline
     */
    private static final String ANOMALY_DETECTION_PROMPT = """
            You are a collaboration anomaly detector. Analyze this team's commit history and detect suspicious patterns.
            
            Assignment Period: %s to %s (%d days)
            Total Commits: %d
            Team Members: %s
            
            Commit Timeline:
            %s
            
            Detect these anomalies:
            - LATE_DUMP: >50%% of commits in last 20%% of time period
            - SOLO_DEVELOPMENT: One person has >70%% of commits
            - INACTIVE_PERIOD: Gap of >50%% of assignment period with no commits
            - UNEVEN_DISTRIBUTION: Commits clustered in short bursts rather than spread out
            
            Respond ONLY with valid JSON:
            {"flags": ["LATE_DUMP", "SOLO_DEVELOPMENT"], "confidence": 0.85, "reasons": ["60%% of commits in last 2 days", "Alice has 75%% of commits"]}
            
            Valid flags: LATE_DUMP, SOLO_DEVELOPMENT, INACTIVE_PERIOD, UNEVEN_DISTRIBUTION
            """;

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    public AnomalyDetectorService(ChatClient chatClient, AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
    }

    /**
     * Detects collaboration anomalies in team commit history.
     *
     * @param request anomaly detection request with team commits and assignment dates
     * @return anomaly report with detected flags and explanations
     */
    public AnomalyReportDTO detect(AnomalyDetectionRequestDTO request) {
        if (!aiProperties.isEnabled() || !aiProperties.getAnomalyDetector().isEnabled()) {
            log.debug("Anomaly detector is disabled");
            return new AnomalyReportDTO(List.of(), 0.0, List.of("AI disabled"));
        }

        log.info("Detecting anomalies for team: {}", request.teamId());

        // LLM for pattern recognition (primary)
        String prompt = buildPrompt(request);
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        AnomalyReportDTO llmResult = parseResponse(response);

        // Rule-based validation (verify LLM findings with exact math)
        AnomalyReportDTO validatedResult = validateWithRules(request, llmResult);

        if (validatedResult.confidence() < aiProperties.getAnomalyDetector().getConfidenceThreshold()) {
            log.debug("Low confidence ({}) for team {}, returning no anomalies",
                    validatedResult.confidence(), request.teamId());
            return new AnomalyReportDTO(List.of(), validatedResult.confidence(),
                    List.of("Low confidence: " + String.join(", ", validatedResult.reasons())));
        }

        return validatedResult;
    }

    /**
     * Validates LLM findings with rule-based calculations.
     * Corrects percentages and adds missing obvious anomalies.
     */
    private AnomalyReportDTO validateWithRules(AnomalyDetectionRequestDTO request, AnomalyReportDTO llmResult) {
        List<AnomalyFlag> flags = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        // Calculate exact statistics
        Map<String, Long> commitsByAuthor = request.commits().stream()
                .collect(Collectors.groupingBy(
                        AnomalyDetectionRequestDTO.CommitSummary::author,
                        Collectors.counting()
                ));

        long totalCommits = request.commits().size();
        long totalDays = Duration.between(request.assignmentStart(), request.assignmentEnd()).toDays();
        long lastPeriodDays = Math.max(1, totalDays / 5); // Last 20% of time

        // SOLO_DEVELOPMENT: One person > 70% of commits
        commitsByAuthor.forEach((author, count) -> {
            double percentage = (count * 100.0) / totalCommits;
            if (percentage > 70) {
                flags.add(AnomalyFlag.SOLO_DEVELOPMENT);
                reasons.add(String.format("%s has %.1f%% of commits (%d/%d)", 
                        author, percentage, count, totalCommits));
            }
        });

        // LATE_DUMP: >50% of commits in last 20% of time
        long commitsInLastPeriod = request.commits().stream()
                .filter(c -> Duration.between(c.timestamp(), request.assignmentEnd()).toDays() <= lastPeriodDays)
                .count();
        double lateDumpPercentage = (commitsInLastPeriod * 100.0) / totalCommits;
        if (lateDumpPercentage > 50) {
            flags.add(AnomalyFlag.LATE_DUMP);
            reasons.add(String.format("%.1f%% of commits (%d/%d) in last %d days",
                    lateDumpPercentage, commitsInLastPeriod, totalCommits, lastPeriodDays));
        }

        // INACTIVE_PERIOD: Gap > 50% of assignment period
        List<LocalDateTime> sortedTimestamps = request.commits().stream()
                .map(AnomalyDetectionRequestDTO.CommitSummary::timestamp)
                .sorted()
                .toList();
        long maxGapDays = 0;
        for (int i = 1; i < sortedTimestamps.size(); i++) {
            long gapDays = Duration.between(sortedTimestamps.get(i-1), sortedTimestamps.get(i)).toDays();
            maxGapDays = Math.max(maxGapDays, gapDays);
        }
        if (maxGapDays > totalDays * 0.5) {
            flags.add(AnomalyFlag.INACTIVE_PERIOD);
            reasons.add(String.format("%d-day gap (%.1f%% of assignment period)",
                    maxGapDays, (maxGapDays * 100.0) / totalDays));
        }

        // Merge LLM findings with rule-based corrections
        List<AnomalyFlag> mergedFlags = new ArrayList<>(llmResult.flags());
        List<String> mergedReasons = new ArrayList<>(llmResult.reasons());

        // Add rule-based flags if LLM missed them
        for (AnomalyFlag flag : flags) {
            if (!mergedFlags.contains(flag)) {
                mergedFlags.add(flag);
            }
        }

        // Replace LLM reasons with exact calculations
        if (!flags.isEmpty()) {
            mergedReasons = reasons;
        }

        // Keep LLM confidence if it found something, otherwise use rule-based
        double confidence = mergedFlags.isEmpty() ? 0.0 : 
                (flags.isEmpty() ? llmResult.confidence() : 1.0);

        return new AnomalyReportDTO(mergedFlags, confidence, mergedReasons);
    }

    private String buildPrompt(AnomalyDetectionRequestDTO request) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Group commits by author
        Map<String, Long> commitsByAuthor = request.commits().stream()
                .collect(Collectors.groupingBy(
                        AnomalyDetectionRequestDTO.CommitSummary::author,
                        Collectors.counting()
                ));

        // Calculate time distribution
        long totalDays = Duration.between(request.assignmentStart(), request.assignmentEnd()).toDays();

        return String.format(ANOMALY_DETECTION_PROMPT,
                request.assignmentStart().format(formatter),
                request.assignmentEnd().format(formatter),
                totalDays,
                request.commits().size(),
                commitsByAuthor.keySet(),
                formatCommitTimeline(request.commits())
        );
    }

    private String formatCommitTimeline(List<AnomalyDetectionRequestDTO.CommitSummary> commits) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return commits.stream()
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .limit(20) // Show first 20 commits
                .map(c -> String.format("%s - %s (%d lines)",
                        c.timestamp().format(formatter), c.author(), c.linesChanged()))
                .collect(Collectors.joining("\n"));
    }

    private AnomalyReportDTO parseResponse(String response) {
        try {
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // Parse flags array
            List<AnomalyFlag> flags = new ArrayList<>();
            int flagsStart = cleaned.indexOf("\"flags\": [") + 10;
            int flagsEnd = cleaned.indexOf("]", flagsStart);
            String flagsStr = cleaned.substring(flagsStart, flagsEnd);
            for (String flag : flagsStr.split(",")) {
                String cleanFlag = flag.trim().replace("\"", "");
                if (!cleanFlag.isEmpty()) {
                    flags.add(AnomalyFlag.valueOf(cleanFlag));
                }
            }

            // Parse confidence
            int confStart = cleaned.indexOf("\"confidence\": ") + 15;
            int confEnd = cleaned.indexOf(",", confStart);
            if (confEnd == -1) {
                confEnd = cleaned.indexOf("}", confStart);
            }
            double confidence = Double.parseDouble(cleaned.substring(confStart, confEnd).trim());

            // Parse reasons array
            List<String> reasons = new ArrayList<>();
            int reasonsStart = cleaned.indexOf("\"reasons\": [") + 12;
            int reasonsEnd = cleaned.lastIndexOf("]");
            String reasonsStr = cleaned.substring(reasonsStart, reasonsEnd);
            for (String reason : reasonsStr.split("\",\\s*\"")) {
                String cleanReason = reason.replace("\"", "").trim();
                if (!cleanReason.isEmpty()) {
                    reasons.add(cleanReason);
                }
            }

            return new AnomalyReportDTO(flags, confidence, reasons);
        } catch (Exception e) {
            log.error("Failed to parse anomaly detection response: {}", response, e);
            return new AnomalyReportDTO(List.of(), 0.0, List.of("Parse error: " + e.getMessage()));
        }
    }
}

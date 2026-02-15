package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.analysis.dto.cqi.*;
import de.tum.cit.aet.dataProcessing.service.TeamScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main service for calculating the Collaboration Quality Index (CQI).
 * <p>
 * Formula: CQI = BASE_SCORE × PENALTY_MULTIPLIER
 * <p>
 * BASE_SCORE = w1·S_effort + w2·S_loc + w3·S_temporal + w4·S_ownership + w5·S_pairProgramming
 * <p>
 * Components:
 * - Effort Balance (40%): Distribution of LLM-weighted effort
 * - LoC Balance (25%): Distribution of lines of code
 * - Temporal Spread (20%): How work is spread over time
 * - Ownership Spread (15%): How files are shared among team members
 * - Pair Programming (10%, optional): Verification of collaboration during paired sessions
 * <p>
 * This service works with LLM-rated commits directly.
 * Commits should be pre-filtered using {@link CommitPreFilterService} before LLM analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CQICalculatorService {

    private final CQIConfig cqiConfig;
    private final TeamScheduleService teamScheduleService;
    private final PairProgrammingCalculator pairProgrammingCalculator;

    // Component weights (must sum to 1.0)
    private static final double W_EFFORT = 0.40;
    private static final double W_LOC = 0.25;
    private static final double W_TEMPORAL = 0.20;
    private static final double W_OWNERSHIP = 0.15;

    // Penalty thresholds
    private static final double SOLO_DEV_THRESHOLD = 0.85;          // >85% = solo development
    private static final double SEVERE_IMBALANCE_THRESHOLD = 0.70;  // >70% = severe imbalance
    private static final double HIGH_TRIVIAL_THRESHOLD = 0.50;      // >50% trivial commits
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.40;    // >40% low confidence
    private static final double LATE_WORK_THRESHOLD = 0.50;         // >50% work in final 20%

    // Confidence threshold
    private static final double LOW_CONFIDENCE_VALUE = 0.6;

    /**
     * Input for CQI calculation: rated commit chunk.
     */
    public record RatedChunk(CommitChunkDTO chunk, EffortRatingDTO rating) {
    }

    /**
     * Calculate CQI from LLM-rated commits.
     * <p>
     * The rated chunks should come from commits that passed the pre-filter.
     * This method uses the LLM ratings directly - no post-filtering is applied.
     *
     * @param ratedChunks   Commits with LLM effort ratings
     * @param teamSize      Number of team members
     * @param projectStart  Project start date
     * @param projectEnd    Project end date
     * @param filterSummary Summary from pre-filtering (optional)
     * @param teamName      The team name
     * @return CQI result with component scores and penalties
     */
    public CQIResultDTO calculate(
            List<RatedChunk> ratedChunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd,
            FilterSummaryDTO filterSummary,
            String teamName) {

        ComponentWeightsDTO weightsDTO = buildWeightsDTO();

        // Edge case: single contributor
        if (teamSize <= 1) {
            log.info("Single contributor detected - CQI = 0");
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        // Edge case: no commits to analyze
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            log.warn("No rated commits provided");
            return CQIResultDTO.noProductiveWork(weightsDTO, filterSummary);
        }

        // Edge case: < 2/3 pair programming sessions were attended
        if (teamName != null && !teamName.isEmpty() && !teamScheduleService.isPairedAtLeastTwoOfThree(teamName)) {
            log.warn("Less then 2/3 pair programming sessions were attended.");
            return CQIResultDTO.noPairProgramming(weightsDTO);
        }

        // Aggregate metrics by author
        Map<Long, Double> effortByAuthor = aggregateEffort(ratedChunks);
        Map<Long, Integer> locByAuthor = aggregateLoc(ratedChunks);
        Map<CommitLabel, Integer> commitsByType = aggregateCommitTypes(ratedChunks);

        // Check if we have actual contributors
        if (effortByAuthor.size() <= 1) {
            log.info("Only one contributor found in commits - CQI = 0");
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        // Calculate component scores
        double effortScore = calculateEffortBalance(effortByAuthor);
        double locScore = calculateLocBalance(locByAuthor);
        double temporalScore = calculateTemporalSpread(ratedChunks, projectStart, projectEnd);
        double ownershipScore = calculateOwnershipSpread(ratedChunks, teamSize);

        ComponentScoresDTO components = new ComponentScoresDTO(
                effortScore, locScore, temporalScore, ownershipScore, null);

        log.debug("Component scores: {}", components.toSummary());

        // Calculate base score (weighted sum)
        CQIConfig.Weights weights = cqiConfig.getWeights();
        double baseScore = components.weightedSum(
                weights.getEffort(), weights.getLoc(), weights.getTemporal(), weights.getOwnership());

        // Calculate penalties
        List<CQIPenaltyDTO> penalties = calculatePenalties(
                effortByAuthor, commitsByType, ratedChunks, projectStart, projectEnd);

        double penaltyMultiplier = penalties.isEmpty() ? 1.0 :
                penalties.stream()
                        .mapToDouble(CQIPenaltyDTO::multiplier)
                        .reduce(1.0, (a, b) -> a * b);

        // Final CQI
        double cqi = Math.max(0, Math.min(100, baseScore * penaltyMultiplier));

        log.info("CQI calculated: {} (base={}, penalty={})",
                String.format("%.1f", cqi),
                String.format("%.1f", baseScore),
                String.format("%.2f", penaltyMultiplier));

        return new CQIResultDTO(cqi, components, weightsDTO, penalties, baseScore, penaltyMultiplier, filterSummary);
    }

    /**
     * Calculate CQI using fallback (when LLM is unavailable).
     * Uses only Lines of Code distribution.
     * <p>
     * Note: Commits should still be pre-filtered before calling this method.
     *
     * @param chunks        Pre-filtered commit chunks (not rated)
     * @param teamSize      Number of team members
     * @param filterSummary Summary from pre-filtering
     * @return CQI result based on LoC only
     */
    public CQIResultDTO calculateFallback(
            List<CommitChunkDTO> chunks,
            int teamSize,
            FilterSummaryDTO filterSummary) {

        ComponentWeightsDTO weightsDTO = buildWeightsDTO();

        if (teamSize <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        if (chunks == null || chunks.isEmpty()) {
            return CQIResultDTO.noProductiveWork(weightsDTO, filterSummary);
        }

        // Aggregate LoC by author from raw chunks
        Map<Long, Integer> locByAuthor = chunks.stream()
                .filter(c -> c.authorId() != null)
                .collect(Collectors.groupingBy(
                        CommitChunkDTO::authorId,
                        Collectors.summingInt(CommitChunkDTO::totalLinesChanged)
                ));

        if (locByAuthor.size() <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        double locScore = calculateLocBalance(locByAuthor);

        log.info("Fallback CQI calculated (LoC only): {}", String.format("%.1f", locScore));

        return CQIResultDTO.fallback(weightsDTO, locScore, filterSummary);
    }

    /**
     * Calculate only the git-based component scores (no AI/LLM required).
     * This can be called immediately after git analysis to show partial metrics.
     * <p>
     * Calculates:
     * - LoC Balance (25%): Distribution of lines of code
     * - Temporal Spread (20%): How work is spread over time
     * - Ownership Spread (15%): How files are shared among team members
     * - Pair Programming (10%, optional): Collaboration during paired sessions
     * <p>
     * Effort Balance (40%) requires AI and will be 0.
     *
     * @param chunks       Pre-filtered commit chunks (not rated)
     * @param teamSize     Number of team members
     * @param projectStart Project start date (optional, uses first commit if null)
     * @param projectEnd   Project end date (optional, uses last commit if null)
     * @param teamName     Team name for retrieving paired session data (optional)
     * @return Component scores with only git-based metrics filled in
     */
    public ComponentScoresDTO calculateGitOnlyComponents(
            List<CommitChunkDTO> chunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd,
            String teamName) {

        if (chunks == null || chunks.isEmpty() || teamSize <= 1) {
            return ComponentScoresDTO.zero();
        }

        // Aggregate LoC by author
        Map<Long, Integer> locByAuthor = chunks.stream()
                .filter(c -> c.authorId() != null)
                .collect(Collectors.groupingBy(
                        CommitChunkDTO::authorId,
                        Collectors.summingInt(CommitChunkDTO::totalLinesChanged)
                ));

        if (locByAuthor.size() <= 1) {
            return ComponentScoresDTO.zero();
        }

        // Calculate LoC balance
        double locScore = calculateLocBalance(locByAuthor);

        // Determine project boundaries from commits if not provided
        LocalDateTime effectiveStart = projectStart;
        LocalDateTime effectiveEnd = projectEnd;

        if (effectiveStart == null || effectiveEnd == null) {
            List<LocalDateTime> timestamps = chunks.stream()
                    .map(CommitChunkDTO::timestamp)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();

            if (!timestamps.isEmpty()) {
                if (effectiveStart == null) {
                    effectiveStart = timestamps.get(0);
                }
                if (effectiveEnd == null) {
                    effectiveEnd = timestamps.get(timestamps.size() - 1);
                }
            }
        }

        // Calculate temporal spread using raw chunks
        double temporalScore = calculateTemporalSpreadFromChunks(chunks, effectiveStart, effectiveEnd);

        // Calculate ownership spread using raw chunks
        double ownershipScore = calculateOwnershipSpreadFromChunks(chunks, teamSize);

        // Calculate pair programming score if team name is provided
        Double pairProgrammingScore = null;
        log.info("calculateGitOnlyComponents: teamName={}, teamSize={}", teamName, teamSize);
        if (teamName != null && teamSize == 2) {
            try {
                Set<OffsetDateTime> pairedSessions = teamScheduleService.getPairedSessions(teamName);
                Set<OffsetDateTime> allSessions = teamScheduleService.getClassDates(teamName);

                // Log all session dates for verification
                log.info("=== Pair Programming Session Dates for team '{}' ===", teamName);
                log.info("Total sessions: {}", allSessions.size());
                allSessions.stream().sorted().forEach(date ->
                    log.info("  All session: {}", date));

                log.info("Paired sessions (both students attended): {}", pairedSessions.size());
                pairedSessions.stream().sorted().forEach(date ->
                    log.info("  Paired session: {}", date));

                if (pairedSessions != null && !pairedSessions.isEmpty() && allSessions != null && !allSessions.isEmpty()) {
                    pairProgrammingScore = pairProgrammingCalculator.calculateFromChunks(
                            pairedSessions, allSessions, chunks, teamSize);
                    log.info("Pair programming score calculated for team {}: {} (based on commits during paired sessions)",
                            teamName,
                            pairProgrammingScore != null ? String.format("%.1f", pairProgrammingScore) : "null");
                } else {
                    log.info("No paired sessions or class dates found for team {}: paired={}, all={}",
                            teamName, pairedSessions != null ? pairedSessions.size() : 0,
                            allSessions != null ? allSessions.size() : 0);
                }
            } catch (Exception e) {
                log.error("Failed to calculate pair programming score for team {}: {}", teamName, e.getMessage(), e);
            }
        } else {
            if (teamName == null) {
                log.info("calculateGitOnlyComponents: teamName is null, skipping pair programming calculation");
            } else if (teamSize != 2) {
                log.info("calculateGitOnlyComponents: teamSize={} (not 2), skipping pair programming calculation", teamSize);
            }
        }

        log.debug("Git-only components: LoC={}, Temporal={}, Ownership={}, PairProgramming={}",
                String.format("%.1f", locScore),
                String.format("%.1f", temporalScore),
                String.format("%.1f", ownershipScore),
                pairProgrammingScore != null ? String.format("%.1f", pairProgrammingScore) : "N/A");

        // effortBalance = 0 because it requires AI
        return new ComponentScoresDTO(0.0, locScore, temporalScore, ownershipScore, pairProgrammingScore);
    }

    /**
     * Backward compatible overload for calculateGitOnlyComponents without teamName.
     * Use the 5-parameter version when pair programming needs to be calculated.
     *
     * @param chunks List of raw commit chunks from repository
     * @param teamSize Number of team members
     * @param projectStart Start date/time of the project
     * @param projectEnd End date/time of the project
     * @return ComponentScoresDTO containing git-based metrics (locBalance, temporalSpread, ownershipSpread)
     */
    public ComponentScoresDTO calculateGitOnlyComponents(
            List<CommitChunkDTO> chunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd) {
        return calculateGitOnlyComponents(chunks, teamSize, projectStart, projectEnd, null);
    }

    /**
     * Build a {@link ComponentWeightsDTO} from the current configuration.
     *
     * @return DTO containing the configured weights for all CQI components
     */
    public ComponentWeightsDTO buildWeightsDTO() {
        CQIConfig.Weights w = cqiConfig.getWeights();
        return new ComponentWeightsDTO(w.getEffort(), w.getLoc(), w.getTemporal(), w.getOwnership());
    }

    /**
     * Calculate temporal spread from raw commit chunks (no AI rating needed).
     */
    private double calculateTemporalSpreadFromChunks(List<CommitChunkDTO> chunks,
                                                      LocalDateTime projectStart,
                                                      LocalDateTime projectEnd) {
        if (chunks.isEmpty() || projectStart == null || projectEnd == null) {
            return 50.0; // Neutral score if no temporal data
        }

        long totalDays = ChronoUnit.DAYS.between(projectStart, projectEnd);
        if (totalDays <= 0) {
            return 50.0;
        }

        // Divide project into weeks
        int numWeeks = Math.max(1, (int) Math.ceil(totalDays / 7.0));
        double[] weeklyLines = new double[numWeeks];

        for (CommitChunkDTO chunk : chunks) {
            if (chunk.timestamp() == null) {
                continue;
            }

            long daysSinceStart = ChronoUnit.DAYS.between(projectStart, chunk.timestamp());
            int weekIndex = Math.min((int) (daysSinceStart / 7), numWeeks - 1);
            weekIndex = Math.max(0, weekIndex);

            // Use lines changed as proxy for effort
            weeklyLines[weekIndex] += chunk.totalLinesChanged();
        }

        // Calculate coefficient of variation
        double mean = Arrays.stream(weeklyLines).average().orElse(0);
        if (mean == 0) {
            return 50.0;
        }

        double variance = Arrays.stream(weeklyLines)
                .map(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdev = Math.sqrt(variance);
        double cv = stdev / mean;

        // Normalize: CV of 0 = perfect (score 100), CV of 2+ = poor (score 0)
        double normalizedCV = Math.min(cv / 2.0, 1.0);

        return 100.0 * (1.0 - normalizedCV);
    }

    /**
     * Calculate ownership spread from raw commit chunks (no AI rating needed).
     */
    private double calculateOwnershipSpreadFromChunks(List<CommitChunkDTO> chunks, int teamSize) {
        if (chunks.isEmpty() || teamSize <= 1) {
            return 0.0;
        }

        // Map: filename -> set of author IDs
        Map<String, Set<Long>> fileAuthors = new HashMap<>();
        Map<String, Integer> fileCommitCounts = new HashMap<>();

        for (CommitChunkDTO chunk : chunks) {
            if (chunk.files() == null) {
                continue;
            }
            for (String file : chunk.files()) {
                fileAuthors.computeIfAbsent(file, k -> new HashSet<>()).add(chunk.authorId());
                fileCommitCounts.merge(file, 1, Integer::sum);
            }
        }

        // Filter to significant files (>= 3 commits)
        List<String> significantFiles = fileAuthors.keySet().stream()
                .filter(f -> fileCommitCounts.getOrDefault(f, 0) >= 3)
                .toList();

        if (significantFiles.isEmpty()) {
            return 75.0; // Neutral for sparse data
        }

        // Calculate average author count per file (capped at team size)
        int effectiveTeamSize = Math.min(teamSize, 4); // Cap to avoid penalizing small teams
        double totalAuthors = significantFiles.stream()
                .mapToDouble(f -> Math.min(fileAuthors.get(f).size(), effectiveTeamSize))
                .sum();

        double maxPossible = significantFiles.size() * effectiveTeamSize;
        return 100.0 * totalAuthors / maxPossible;
    }

    // ==================== Component Calculations ====================

    /**
     * Calculate effort balance score using Gini coefficient.
     * Score = 100 × (1 - Gini)
     */
    private double calculateEffortBalance(Map<Long, Double> effortByAuthor) {
        if (effortByAuthor == null || effortByAuthor.size() <= 1) {
            return 0.0;
        }

        double[] efforts = effortByAuthor.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

        double gini = calculateGiniCoefficient(efforts);
        return 100.0 * (1.0 - gini);
    }

    /**
     * Calculate LoC balance score using Gini coefficient.
     * Score = 100 × (1 - Gini)
     */
    private double calculateLocBalance(Map<Long, Integer> locByAuthor) {
        if (locByAuthor == null || locByAuthor.size() <= 1) {
            return 0.0;
        }

        double[] locs = locByAuthor.values().stream()
                .mapToDouble(Integer::doubleValue)
                .toArray();

        double gini = calculateGiniCoefficient(locs);
        return 100.0 * (1.0 - gini);
    }

    /**
     * Calculate temporal spread score.
     * Rewards work spread evenly over project duration, penalizes cramming.
     */
    private double calculateTemporalSpread(List<RatedChunk> chunks,
                                           LocalDateTime projectStart,
                                           LocalDateTime projectEnd) {
        if (chunks.isEmpty() || projectStart == null || projectEnd == null) {
            return 50.0; // Neutral score if no temporal data
        }

        long totalDays = ChronoUnit.DAYS.between(projectStart, projectEnd);
        if (totalDays <= 0) {
            return 50.0;
        }

        // Divide project into weeks
        int numWeeks = Math.max(1, (int) Math.ceil(totalDays / 7.0));
        double[] weeklyEffort = new double[numWeeks];

        for (RatedChunk rc : chunks) {
            if (rc.chunk().timestamp() == null) {
                continue;
            }

            long daysSinceStart = ChronoUnit.DAYS.between(projectStart, rc.chunk().timestamp());
            int weekIndex = Math.min((int) (daysSinceStart / 7), numWeeks - 1);
            weekIndex = Math.max(0, weekIndex);

            double effort = rc.rating() != null ? rc.rating().weightedEffort() : 1.0;
            weeklyEffort[weekIndex] += effort;
        }

        // Calculate coefficient of variation
        double mean = Arrays.stream(weeklyEffort).average().orElse(0);
        if (mean == 0) {
            return 50.0;
        }

        double variance = Arrays.stream(weeklyEffort)
                .map(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdev = Math.sqrt(variance);
        double cv = stdev / mean;

        // Normalize: CV of 0 = perfect (score 100), CV of 2+ = poor (score 0)
        double normalizedCV = Math.min(cv / 2.0, 1.0);

        return 100.0 * (1.0 - normalizedCV);
    }

    /**
     * Calculate ownership spread score.
     * Rewards multiple authors touching the same files.
     */
    private double calculateOwnershipSpread(List<RatedChunk> chunks, int teamSize) {
        if (chunks.isEmpty() || teamSize <= 1) {
            return 0.0;
        }

        // Map: filename -> set of author IDs
        Map<String, Set<Long>> fileAuthors = new HashMap<>();
        Map<String, Integer> fileCommitCounts = new HashMap<>();

        for (RatedChunk rc : chunks) {
            for (String file : rc.chunk().files()) {
                fileAuthors.computeIfAbsent(file, k -> new HashSet<>()).add(rc.chunk().authorId());
                fileCommitCounts.merge(file, 1, Integer::sum);
            }
        }

        // Filter to significant files (>= 3 commits)
        List<String> significantFiles = fileAuthors.keySet().stream()
                .filter(f -> fileCommitCounts.getOrDefault(f, 0) >= 3)
                .toList();

        if (significantFiles.isEmpty()) {
            return 75.0; // Neutral for sparse data
        }

        // Calculate average author count per file (capped at team size)
        int effectiveTeamSize = Math.min(teamSize, 4); // Cap to avoid penalizing small teams
        double totalAuthors = significantFiles.stream()
                .mapToDouble(f -> Math.min(fileAuthors.get(f).size(), effectiveTeamSize))
                .sum();

        double maxPossible = significantFiles.size() * effectiveTeamSize;
        return 100.0 * totalAuthors / maxPossible;
    }

    // ==================== Penalty Calculations ====================

    /**
     * Calculate all applicable penalties.
     */
    private List<CQIPenaltyDTO> calculatePenalties(
            Map<Long, Double> effortByAuthor,
            Map<CommitLabel, Integer> commitsByType,
            List<RatedChunk> chunks,
            LocalDateTime projectStart,
            LocalDateTime projectEnd) {

        // Penalties are disabled - return empty list
        // The CQI score is now purely based on the component scores
        return new ArrayList<>();
    }

    /**
     * Calculate the ratio of work done in the final 20% of the project.
     */
    private double calculateLateWorkRatio(List<RatedChunk> chunks,
                                          LocalDateTime projectStart,
                                          LocalDateTime projectEnd) {
        if (chunks.isEmpty() || projectStart == null || projectEnd == null) {
            return 0.0;
        }

        long totalDays = ChronoUnit.DAYS.between(projectStart, projectEnd);
        if (totalDays <= 0) {
            return 0.0;
        }

        long latePeriodStart = (long) (totalDays * 0.8);
        LocalDateTime lateDate = projectStart.plusDays(latePeriodStart);

        double totalEffort = chunks.stream()
                .mapToDouble(rc -> rc.rating() != null ? rc.rating().weightedEffort() : 1.0)
                .sum();

        if (totalEffort == 0) {
            return 0.0;
        }

        double lateEffort = chunks.stream()
                .filter(rc -> rc.chunk().timestamp() != null && rc.chunk().timestamp().isAfter(lateDate))
                .mapToDouble(rc -> rc.rating() != null ? rc.rating().weightedEffort() : 1.0)
                .sum();

        return lateEffort / totalEffort;
    }

    // ==================== Helper Methods ====================

    /**
     * Calculate Gini coefficient for distribution fairness.
     * 0 = perfect equality, 1 = complete inequality
     */
    private double calculateGiniCoefficient(double[] values) {
        int n = values.length;
        if (n <= 1) {
            return 0.0;
        }

        double sum = Arrays.stream(values).sum();
        if (sum == 0) {
            return 1.0; // Complete inequality if no contributions
        }

        double sumOfDifferences = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                sumOfDifferences += Math.abs(values[i] - values[j]);
            }
        }

        return sumOfDifferences / (2.0 * n * sum);
    }

    /**
     * Aggregate weighted effort by author.
     */
    private Map<Long, Double> aggregateEffort(List<RatedChunk> chunks) {
        return chunks.stream()
                .filter(rc -> rc.chunk().authorId() != null)
                .collect(Collectors.groupingBy(
                        rc -> rc.chunk().authorId(),
                        Collectors.summingDouble(rc ->
                                rc.rating() != null ? rc.rating().weightedEffort() : 1.0)
                ));
    }

    /**
     * Aggregate lines changed by author.
     */
    private Map<Long, Integer> aggregateLoc(List<RatedChunk> chunks) {
        return chunks.stream()
                .filter(rc -> rc.chunk().authorId() != null)
                .collect(Collectors.groupingBy(
                        rc -> rc.chunk().authorId(),
                        Collectors.summingInt(rc -> rc.chunk().totalLinesChanged())
                ));
    }

    /**
     * Aggregate commit types from LLM classifications.
     */
    private Map<CommitLabel, Integer> aggregateCommitTypes(List<RatedChunk> chunks) {
        return chunks.stream()
                .filter(rc -> rc.rating() != null && rc.rating().type() != null)
                .collect(Collectors.groupingBy(
                        rc -> rc.rating().type(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }
}

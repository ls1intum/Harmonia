package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.dto.cqi.*;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
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
 * Formula: CQI = w1·S_effort + w2·S_loc + w3·S_temporal + w4·S_ownership
 * <p>
 * Component weights are configurable via {@link CQIConfig}.
 * <p>
 * This service works with LLM-rated commits directly.
 * Commits should be pre-filtered using {@link CommitPreFilterService} before LLM analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CQICalculatorService {

    private final CQIConfig cqiConfig;
    private final PairProgrammingService pairProgrammingService;
    private final PairProgrammingCalculator pairProgrammingCalculator;

    /**
     * Result of temporal spread calculation, containing both the score and the daily distribution data.
     */
    public record TemporalSpreadResultDTO(double score, List<Double> dailyDistribution) {
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
     * @return CQI result with component scores
     */
    public CQIResultDTO calculate(
            List<CqiRatedChunkDTO> ratedChunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd,
            FilterSummaryDTO filterSummary,
            String teamName) {

        // --- 1. Build weights and check edge cases ---
        ComponentWeightsDTO weightsDTO = buildWeightsDTO();

        if (teamSize <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            return CQIResultDTO.noProductiveWork(weightsDTO, filterSummary);
        }
        if (teamName != null && !teamName.isEmpty()
                && pairProgrammingService.getTeamAttendance(teamName) != null
                && !pairProgrammingService.hasCancelledSessionWarning(teamName)
                && !pairProgrammingService.isPairedMandatorySessions(teamName)) {
            return CQIResultDTO.noPairProgramming(weightsDTO);
        }

        // --- 2. Aggregate metrics by author ---
        Map<Long, Double> effortByAuthor = aggregateEffort(ratedChunks);
        Map<Long, Integer> locByAuthor = aggregateLoc(ratedChunks);

        if (effortByAuthor.size() <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        // --- 3. Calculate component scores ---
        double effortScore = calculateEffortBalance(effortByAuthor);
        double locScore = calculateLocBalance(locByAuthor);
        TemporalSpreadResultDTO temporalResult = calculateTemporalSpread(ratedChunks, projectStart, projectEnd);
        double ownershipScore = calculateOwnershipSpread(ratedChunks, teamSize);

        ComponentScoresDTO components = new ComponentScoresDTO(
                effortScore, locScore, temporalResult.score(), ownershipScore, null, null,
                temporalResult.dailyDistribution());

        // --- 4. Compute weighted CQI ---
        CQIConfig.Weights weights = cqiConfig.getWeights();
        double baseScore = components.weightedSum(
                weights.getEffort(), weights.getLoc(), weights.getTemporal(), weights.getOwnership());
        double cqi = Math.max(0, Math.min(100, baseScore));
        return new CQIResultDTO(cqi, components, weightsDTO, baseScore, filterSummary);
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

        // 1) Validate input
        ComponentWeightsDTO weightsDTO = buildWeightsDTO();
        if (teamSize <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }
        if (chunks == null || chunks.isEmpty()) {
            return CQIResultDTO.noProductiveWork(weightsDTO, filterSummary);
        }

        // 2) Aggregate LoC by author
        Map<Long, Integer> locByAuthor = chunks.stream()
                .filter(c -> c.authorId() != null)
                .collect(Collectors.groupingBy(
                        CommitChunkDTO::authorId,
                        Collectors.summingInt(CommitChunkDTO::totalLinesChanged)
                ));

        if (locByAuthor.size() <= 1) {
            return CQIResultDTO.singleContributor(weightsDTO);
        }

        // 3) Return LoC-only CQI
        double locScore = calculateLocBalance(locByAuthor);
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

        // --- 1. Validate input ---
        if (chunks == null || chunks.isEmpty() || teamSize <= 1) {
            return ComponentScoresDTO.zero();
        }

        // --- 2. Aggregate LoC by author ---
        Map<Long, Integer> locByAuthor = chunks.stream()
                .filter(c -> c.authorId() != null)
                .collect(Collectors.groupingBy(
                        CommitChunkDTO::authorId,
                        Collectors.summingInt(CommitChunkDTO::totalLinesChanged)
                ));

        if (locByAuthor.size() <= 1) {
            return ComponentScoresDTO.zero();
        }

        // --- 3. Calculate git-based component scores ---
        double locScore = calculateLocBalance(locByAuthor);

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

        TemporalSpreadResultDTO temporalResult = calculateTemporalSpreadFromChunks(chunks, effectiveStart, effectiveEnd);
        double ownershipScore = calculateOwnershipSpreadFromChunks(chunks, teamSize);

        // --- 4. Calculate pair programming score (teams of 2 only) ---
        Double pairProgrammingScore = null;
        String pairProgrammingStatus = null;
        if (teamName != null && teamSize == 2) {
            try {
                boolean hasAttendanceData = pairProgrammingService.hasAttendanceData();
                boolean hasTeamAttendance = pairProgrammingService.hasTeamAttendance(teamName);
                boolean hasCancelledSessionWarning = pairProgrammingService.hasCancelledSessionWarning(teamName);

                Set<OffsetDateTime> pairedSessions = pairProgrammingService.getPairedSessions(teamName);
                Set<OffsetDateTime> allSessions = pairProgrammingService.getClassDates(teamName);

                if (hasTeamAttendance) {
                    pairProgrammingStatus = hasCancelledSessionWarning ? "WARNING" : "FOUND";
                    if (hasCancelledSessionWarning) {
                        log.warn("Team '{}' has cancelled sessions affecting mandatory attendance", teamName);
                    } else if (!pairedSessions.isEmpty() && !allSessions.isEmpty()) {
                        pairProgrammingScore = pairProgrammingCalculator.calculateFromChunks(
                                pairedSessions, allSessions, chunks, teamSize);
                    } else {
                        pairProgrammingScore = 0.0;
                    }
                } else if (hasAttendanceData) {
                    pairProgrammingStatus = "NOT_FOUND";
                }
            } catch (Exception e) {
                log.error("Failed to calculate pair programming score for team {}: {}", teamName, e.getMessage(), e);
            }
        }

        // --- 5. Build result (effortBalance = 0 because it requires AI) ---
        return new ComponentScoresDTO(0.0, locScore, temporalResult.score(), ownershipScore, pairProgrammingScore, pairProgrammingStatus,
                temporalResult.dailyDistribution());
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
     * Build a {@link ComponentWeightsDTO} renormalized to exclude effort balance.
     * Used in SIMPLE mode where AI analysis is not run, so the remaining weights
     * (loc, temporal, ownership) are scaled proportionally to sum to 1.0.
     *
     * @return DTO with effortBalance=0 and remaining weights summing to 1.0
     */
    public ComponentWeightsDTO buildRenormalizedWeightsWithoutEffort() {
        CQIConfig.Weights w = cqiConfig.getWeights();
        double divisor = w.getLoc() + w.getTemporal() + w.getOwnership();
        if (divisor <= 0) {
            return new ComponentWeightsDTO(0.0, 0.0, 0.0, 0.0);
        }
        return new ComponentWeightsDTO(0.0, w.getLoc() / divisor, w.getTemporal() / divisor, w.getOwnership() / divisor);
    }

    /**
     * Returns a new {@link CQIResultDTO} with weights renormalized to exclude effort balance.
     * The component scores are preserved, only the weights are adjusted.
     *
     * @param original the original CQI result with standard weights
     * @return a new CQI result with renormalized weights
     */
    public CQIResultDTO renormalizeWithoutEffort(CQIResultDTO original) {
        return new CQIResultDTO(
                original.cqi(), original.components(), buildRenormalizedWeightsWithoutEffort(),
                original.baseScore(), original.filterSummary());
    }

    /**
     * Calculate temporal spread from raw commit chunks (no AI rating needed).
     */
    private TemporalSpreadResultDTO calculateTemporalSpreadFromChunks(List<CommitChunkDTO> chunks,
                                                     LocalDateTime projectStart,
                                                     LocalDateTime projectEnd) {
        if (chunks.isEmpty() || projectStart == null || projectEnd == null) {
            return new TemporalSpreadResultDTO(50.0, List.of());
        }

        long totalDays = ChronoUnit.DAYS.between(projectStart, projectEnd);
        if (totalDays <= 0) {
            return new TemporalSpreadResultDTO(50.0, List.of());
        }

        // Divide project into days
        int numDays = Math.max(1, (int) totalDays);
        double[] dailyLines = new double[numDays];

        for (CommitChunkDTO chunk : chunks) {
            if (chunk.timestamp() == null) {
                continue;
            }

            long daysSinceStart = ChronoUnit.DAYS.between(projectStart, chunk.timestamp());
            int dayIndex = Math.min((int) daysSinceStart, numDays - 1);
            dayIndex = Math.max(0, dayIndex);

            // Use lines changed as proxy for effort
            dailyLines[dayIndex] += chunk.totalLinesChanged();
        }

        List<Double> dailyDistribution = Arrays.stream(dailyLines).boxed().toList();

        // Calculate coefficient of variation
        double mean = Arrays.stream(dailyLines).average().orElse(0);
        if (mean == 0) {
            return new TemporalSpreadResultDTO(50.0, dailyDistribution);
        }

        double variance = Arrays.stream(dailyLines)
                .map(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdev = Math.sqrt(variance);
        double cv = stdev / mean;

        // Normalize: CV of 0 = perfect (score 100), CV of 5+ = poor (score 0)
        double normalizedCV = Math.min(cv / 5.0, 1.0);

        double score = 100.0 * (1.0 - normalizedCV);
        return new TemporalSpreadResultDTO(score, dailyDistribution);
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
    private TemporalSpreadResultDTO calculateTemporalSpread(List<CqiRatedChunkDTO> chunks,
                                           LocalDateTime projectStart,
                                           LocalDateTime projectEnd) {
        if (chunks.isEmpty() || projectStart == null || projectEnd == null) {
            return new TemporalSpreadResultDTO(50.0, List.of());
        }

        long totalDays = ChronoUnit.DAYS.between(projectStart, projectEnd);
        if (totalDays <= 0) {
            return new TemporalSpreadResultDTO(50.0, List.of());
        }

        // Divide project into days
        int numDays = Math.max(1, (int) totalDays);
        double[] dailyEffort = new double[numDays];

        for (CqiRatedChunkDTO rc : chunks) {
            if (rc.chunk().timestamp() == null) {
                continue;
            }

            long daysSinceStart = ChronoUnit.DAYS.between(projectStart, rc.chunk().timestamp());
            int dayIndex = Math.min((int) daysSinceStart, numDays - 1);
            dayIndex = Math.max(0, dayIndex);

            dailyEffort[dayIndex] += rc.chunk().totalLinesChanged();
        }

        List<Double> dailyDistribution = Arrays.stream(dailyEffort).boxed().toList();

        // Calculate coefficient of variation
        double mean = Arrays.stream(dailyEffort).average().orElse(0);
        if (mean == 0) {
            return new TemporalSpreadResultDTO(50.0, dailyDistribution);
        }

        double variance = Arrays.stream(dailyEffort)
                .map(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdev = Math.sqrt(variance);
        double cv = stdev / mean;

        // Normalize: CV of 0 = perfect (score 100), CV of 5+ = poor (score 0)
        double normalizedCV = Math.min(cv / 5.0, 1.0);

        double score = 100.0 * (1.0 - normalizedCV);
        return new TemporalSpreadResultDTO(score, dailyDistribution);
    }

    /**
     * Calculate ownership spread score.
     * Rewards multiple authors touching the same files.
     */
    private double calculateOwnershipSpread(List<CqiRatedChunkDTO> chunks, int teamSize) {
        if (chunks.isEmpty() || teamSize <= 1) {
            return 0.0;
        }

        // Map: filename -> set of author IDs
        Map<String, Set<Long>> fileAuthors = new HashMap<>();
        Map<String, Integer> fileCommitCounts = new HashMap<>();

        for (CqiRatedChunkDTO rc : chunks) {
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
    private Map<Long, Double> aggregateEffort(List<CqiRatedChunkDTO> chunks) {
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
    private Map<Long, Integer> aggregateLoc(List<CqiRatedChunkDTO> chunks) {
        return chunks.stream()
                .filter(rc -> rc.chunk().authorId() != null)
                .collect(Collectors.groupingBy(
                        rc -> rc.chunk().authorId(),
                        Collectors.summingInt(rc -> rc.chunk().totalLinesChanged())
                ));
    }
}

package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.analysis.dto.cqi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main service for calculating the Collaboration Quality Index (CQI).
 * <p>
 * Formula: CQI = BASE_SCORE × PENALTY_MULTIPLIER
 * <p>
 * BASE_SCORE = w1·S_effort + w2·S_loc + w3·S_temporal + w4·S_ownership
 * <p>
 * Components:
 * - Effort Balance (40%): Distribution of LLM-weighted effort
 * - LoC Balance (25%): Distribution of lines of code
 * - Temporal Spread (20%): How work is spread over time
 * - Ownership Spread (15%): How files are shared among team members
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CQICalculatorService {

    private final CommitFilterService filterService;

    // Component weights (must sum to 1.0)
    private static final double W_EFFORT = 0.40;
    private static final double W_LOC = 0.25;
    private static final double W_TEMPORAL = 0.20;
    private static final double W_OWNERSHIP = 0.15;

    // Penalty thresholds
    private static final double SOLO_DEV_THRESHOLD = 0.85;      // >85% = solo development
    private static final double SEVERE_IMBALANCE_THRESHOLD = 0.70; // >70% = severe imbalance
    private static final double HIGH_TRIVIAL_THRESHOLD = 0.50;  // >50% trivial commits
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.40; // >40% low confidence
    private static final double LATE_WORK_THRESHOLD = 0.50;     // >50% work in final 20%

    /**
     * Calculate CQI from pre-filtered input data.
     *
     * @param input CQI input data (already contains filtered chunks)
     * @return CQI result with component scores and penalties
     */
    public CQIResultDTO calculate(CQIInputDTO input) {
        // Edge case: single contributor
        if (input.teamSize() <= 1) {
            log.info("Single contributor detected - CQI = 0");
            return CQIResultDTO.singleContributor();
        }

        // Edge case: no productive commits
        if (input.chunks() == null || input.chunks().isEmpty()) {
            log.warn("No productive commits found");
            return CQIResultDTO.noProductiveWork(null);
        }

        // Filter out already-filtered chunks for calculation
        List<FilteredChunkDTO> productiveChunks = input.chunks().stream()
                .filter(c -> !c.isFiltered())
                .toList();

        if (productiveChunks.isEmpty()) {
            log.warn("All commits were filtered out");
            return CQIResultDTO.noProductiveWork(null);
        }

        // Calculate component scores
        double effortScore = calculateEffortBalance(input.effortByAuthor());
        double locScore = calculateLocBalance(input.locByAuthor());
        double temporalScore = calculateTemporalSpread(productiveChunks, input.projectStart(), input.projectEnd());
        double ownershipScore = calculateOwnershipSpread(productiveChunks, input.teamSize());

        ComponentScoresDTO components = new ComponentScoresDTO(
                effortScore, locScore, temporalScore, ownershipScore);

        log.debug("Component scores: {}", components.toSummary());

        // Calculate base score (weighted sum)
        double baseScore = components.weightedSum(W_EFFORT, W_LOC, W_TEMPORAL, W_OWNERSHIP);

        // Calculate penalties
        List<CQIPenaltyDTO> penalties = calculatePenalties(input, productiveChunks);
        double penaltyMultiplier = penalties.stream()
                .mapToDouble(CQIPenaltyDTO::multiplier)
                .reduce(1.0, (a, b) -> a * b);

        // Final CQI
        double cqi = Math.max(0, Math.min(100, baseScore * penaltyMultiplier));

        log.info("CQI calculated: {:.1f} (base={:.1f}, penalty={:.2f})", cqi, baseScore, penaltyMultiplier);

        return new CQIResultDTO(cqi, components, penalties, baseScore, penaltyMultiplier, null);
    }

    /**
     * Calculate CQI from raw rated chunks (includes filtering step).
     *
     * @param ratedChunks     Raw commit chunks with LLM ratings
     * @param teamSize        Number of team members
     * @param projectStart    Project start date
     * @param projectEnd      Project end date
     * @param avgConfidence   Average LLM confidence
     * @param lowConfCount    Number of low-confidence ratings
     * @return CQI result
     */
    public CQIResultDTO calculateFromRaw(
            List<CommitFilterService.RatedChunk> ratedChunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd,
            double avgConfidence,
            int lowConfCount) {

        // Step 1: Filter commits
        CommitFilterService.FilterResult filterResult = filterService.filterCommits(ratedChunks);

        if (filterResult.productiveChunks().isEmpty()) {
            log.warn("All commits were filtered out");
            return CQIResultDTO.noProductiveWork(filterResult.summary());
        }

        // Step 2: Aggregate effort and LoC by author
        Map<Long, Double> effortByAuthor = aggregateEffort(filterResult.productiveChunks());
        Map<Long, Integer> locByAuthor = aggregateLoc(filterResult.productiveChunks());
        Map<CommitLabel, Integer> commitsByType = aggregateCommitTypes(filterResult.productiveChunks());

        // Step 3: Build input and calculate
        CQIInputDTO input = new CQIInputDTO(
                teamSize,
                effortByAuthor,
                locByAuthor,
                commitsByType,
                filterResult.productiveChunks(),
                projectStart,
                projectEnd,
                avgConfidence,
                lowConfCount
        );

        CQIResultDTO result = calculate(input);

        // Attach filter summary to result
        return new CQIResultDTO(
                result.cqi(),
                result.components(),
                result.penalties(),
                result.baseScore(),
                result.penaltyMultiplier(),
                filterResult.summary()
        );
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
    private double calculateTemporalSpread(List<FilteredChunkDTO> chunks,
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

        for (FilteredChunkDTO chunk : chunks) {
            if (chunk.timestamp() == null) continue;

            long daysSinceStart = ChronoUnit.DAYS.between(projectStart, chunk.timestamp());
            int weekIndex = Math.min((int) (daysSinceStart / 7), numWeeks - 1);
            weekIndex = Math.max(0, weekIndex);

            weeklyEffort[weekIndex] += chunk.effectiveEffort();
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
    private double calculateOwnershipSpread(List<FilteredChunkDTO> chunks, int teamSize) {
        if (chunks.isEmpty() || teamSize <= 1) {
            return 0.0;
        }

        // Map: filename -> set of author IDs
        Map<String, Set<Long>> fileAuthors = new HashMap<>();
        Map<String, Integer> fileCommitCounts = new HashMap<>();

        for (FilteredChunkDTO chunk : chunks) {
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

    // ==================== Penalty Calculations ====================

    /**
     * Calculate all applicable penalties.
     */
    private List<CQIPenaltyDTO> calculatePenalties(CQIInputDTO input, List<FilteredChunkDTO> chunks) {
        List<CQIPenaltyDTO> penalties = new ArrayList<>();

        // Check effort distribution for solo/imbalance
        Map<Long, Double> shares = input.getEffortShares();
        if (!shares.isEmpty()) {
            double maxShare = shares.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);

            if (maxShare > SOLO_DEV_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.soloDevelopment(maxShare));
            } else if (maxShare > SEVERE_IMBALANCE_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.severeImbalance(maxShare));
            }
        }

        // Check trivial ratio
        Map<CommitLabel, Integer> types = input.commitsByType();
        if (types != null && !types.isEmpty()) {
            int total = types.values().stream().mapToInt(Integer::intValue).sum();
            int trivial = types.getOrDefault(CommitLabel.TRIVIAL, 0);
            double trivialRatio = total > 0 ? (double) trivial / total : 0;

            if (trivialRatio > HIGH_TRIVIAL_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.highTrivialRatio(trivialRatio));
            }
        }

        // Check confidence level
        double lowConfRatio = input.getLowConfidenceRatio();
        if (lowConfRatio > LOW_CONFIDENCE_THRESHOLD) {
            penalties.add(CQIPenaltyDTO.lowConfidence(lowConfRatio));
        }

        // Check for late work concentration
        double lateWorkRatio = calculateLateWorkRatio(chunks, input.projectStart(), input.projectEnd());
        if (lateWorkRatio > LATE_WORK_THRESHOLD) {
            penalties.add(CQIPenaltyDTO.lateWork(lateWorkRatio));
        }

        return penalties;
    }

    /**
     * Calculate the ratio of work done in the final 20% of the project.
     */
    private double calculateLateWorkRatio(List<FilteredChunkDTO> chunks,
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

        double totalEffort = chunks.stream().mapToDouble(FilteredChunkDTO::effectiveEffort).sum();
        if (totalEffort == 0) {
            return 0.0;
        }

        double lateEffort = chunks.stream()
                .filter(c -> c.timestamp() != null && c.timestamp().isAfter(lateDate))
                .mapToDouble(FilteredChunkDTO::effectiveEffort)
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
     * Aggregate effective effort by author.
     */
    private Map<Long, Double> aggregateEffort(List<FilteredChunkDTO> chunks) {
        return chunks.stream()
                .filter(c -> !c.isFiltered())
                .collect(Collectors.groupingBy(
                        FilteredChunkDTO::authorId,
                        Collectors.summingDouble(FilteredChunkDTO::effectiveEffort)
                ));
    }

    /**
     * Aggregate effective lines changed by author.
     */
    private Map<Long, Integer> aggregateLoc(List<FilteredChunkDTO> chunks) {
        return chunks.stream()
                .filter(c -> !c.isFiltered())
                .collect(Collectors.groupingBy(
                        FilteredChunkDTO::authorId,
                        Collectors.summingInt(FilteredChunkDTO::effectiveLinesChanged)
                ));
    }

    /**
     * Aggregate commit types.
     */
    private Map<CommitLabel, Integer> aggregateCommitTypes(List<FilteredChunkDTO> chunks) {
        return chunks.stream()
                .filter(c -> !c.isFiltered())
                .collect(Collectors.groupingBy(
                        FilteredChunkDTO::commitType,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }
}

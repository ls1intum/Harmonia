package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
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
 * <p>
 * This service works with LLM-rated commits directly.
 * Commits should be pre-filtered using {@link CommitPreFilterService} before LLM analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CQICalculatorService {

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
     * @return CQI result with component scores and penalties
     */
    public CQIResultDTO calculate(
            List<RatedChunk> ratedChunks,
            int teamSize,
            LocalDateTime projectStart,
            LocalDateTime projectEnd,
            FilterSummaryDTO filterSummary) {

        // Edge case: single contributor
        if (teamSize <= 1) {
            log.info("Single contributor detected - CQI = 0");
            return CQIResultDTO.singleContributor();
        }

        // Edge case: no commits to analyze
        if (ratedChunks == null || ratedChunks.isEmpty()) {
            log.warn("No rated commits provided");
            return CQIResultDTO.noProductiveWork(filterSummary);
        }

        // Aggregate metrics by author
        Map<Long, Double> effortByAuthor = aggregateEffort(ratedChunks);
        Map<Long, Integer> locByAuthor = aggregateLoc(ratedChunks);
        Map<CommitLabel, Integer> commitsByType = aggregateCommitTypes(ratedChunks);

        // Check if we have actual contributors
        if (effortByAuthor.size() <= 1) {
            log.info("Only one contributor found in commits - CQI = 0");
            return CQIResultDTO.singleContributor();
        }

        // Calculate component scores
        double effortScore = calculateEffortBalance(effortByAuthor);
        double locScore = calculateLocBalance(locByAuthor);
        double temporalScore = calculateTemporalSpread(ratedChunks, projectStart, projectEnd);
        double ownershipScore = calculateOwnershipSpread(ratedChunks, teamSize);

        ComponentScoresDTO components = new ComponentScoresDTO(
                effortScore, locScore, temporalScore, ownershipScore);

        log.debug("Component scores: {}", components.toSummary());

        // Calculate base score (weighted sum)
        double baseScore = components.weightedSum(W_EFFORT, W_LOC, W_TEMPORAL, W_OWNERSHIP);

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

        return new CQIResultDTO(cqi, components, penalties, baseScore, penaltyMultiplier, filterSummary);
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

        if (teamSize <= 1) {
            return CQIResultDTO.singleContributor();
        }

        if (chunks == null || chunks.isEmpty()) {
            return CQIResultDTO.noProductiveWork(filterSummary);
        }

        // Aggregate LoC by author from raw chunks
        Map<Long, Integer> locByAuthor = chunks.stream()
                .filter(c -> c.authorId() != null)
                .collect(Collectors.groupingBy(
                        CommitChunkDTO::authorId,
                        Collectors.summingInt(CommitChunkDTO::totalLinesChanged)
                ));

        if (locByAuthor.size() <= 1) {
            return CQIResultDTO.singleContributor();
        }

        double locScore = calculateLocBalance(locByAuthor);

        log.info("Fallback CQI calculated (LoC only): {}", String.format("%.1f", locScore));

        return CQIResultDTO.fallback(locScore, filterSummary);
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

        List<CQIPenaltyDTO> penalties = new ArrayList<>();

        // Check effort distribution for solo/imbalance
        double totalEffort = effortByAuthor.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalEffort > 0) {
            double maxShare = effortByAuthor.values().stream()
                    .mapToDouble(e -> e / totalEffort)
                    .max().orElse(0);

            if (maxShare > SOLO_DEV_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.soloDevelopment(maxShare));
            } else if (maxShare > SEVERE_IMBALANCE_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.severeImbalance(maxShare));
            }
        }

        // Check trivial ratio
        if (commitsByType != null && !commitsByType.isEmpty()) {
            int total = commitsByType.values().stream().mapToInt(Integer::intValue).sum();
            int trivial = commitsByType.getOrDefault(CommitLabel.TRIVIAL, 0);
            double trivialRatio = total > 0 ? (double) trivial / total : 0;

            if (trivialRatio > HIGH_TRIVIAL_THRESHOLD) {
                penalties.add(CQIPenaltyDTO.highTrivialRatio(trivialRatio));
            }
        }

        // Check confidence level
        long lowConfCount = chunks.stream()
                .filter(rc -> rc.rating() != null && rc.rating().confidence() < LOW_CONFIDENCE_VALUE)
                .count();
        double lowConfRatio = chunks.isEmpty() ? 0 : (double) lowConfCount / chunks.size();

        if (lowConfRatio > LOW_CONFIDENCE_THRESHOLD) {
            penalties.add(CQIPenaltyDTO.lowConfidence(lowConfRatio));
        }

        // Check for late work concentration
        double lateWorkRatio = calculateLateWorkRatio(chunks, projectStart, projectEnd);
        if (lateWorkRatio > LATE_WORK_THRESHOLD) {
            penalties.add(CQIPenaltyDTO.lateWork(lateWorkRatio));
        }

        return penalties;
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

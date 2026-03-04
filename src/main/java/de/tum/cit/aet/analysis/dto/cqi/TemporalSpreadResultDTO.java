package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Result of temporal spread calculation, containing both the score and the daily distribution data.
 */
@JsonIgnore
public record TemporalSpreadResultDTO(double score, List<Double> dailyDistribution) {
}
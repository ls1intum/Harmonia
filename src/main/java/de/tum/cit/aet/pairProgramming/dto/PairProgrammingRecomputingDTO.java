package de.tum.cit.aet.pairProgramming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Indicates whether pair programming scores are currently being recomputed for an exercise.
 *
 * @param recomputing true if a recomputation is in progress
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PairProgrammingRecomputingDTO(boolean recomputing) {}

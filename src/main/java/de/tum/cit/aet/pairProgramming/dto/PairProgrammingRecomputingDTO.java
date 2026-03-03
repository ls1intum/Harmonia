package de.tum.cit.aet.pairProgramming.dto;

/**
 * Indicates whether pair programming scores are currently being recomputed for an exercise.
 *
 * @param recomputing true if a recomputation is in progress
 */
public record PairProgrammingRecomputingDTO(boolean recomputing) {}

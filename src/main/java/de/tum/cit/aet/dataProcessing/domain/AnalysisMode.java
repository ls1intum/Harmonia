package de.tum.cit.aet.dataProcessing.domain;

/**
 * Determines the analysis depth for an exercise.
 * <ul>
 *   <li>{@code SIMPLE} — Git analysis + CQI from git-only metrics (no AI/LLM)</li>
 *   <li>{@code FULL} — Git analysis + AI analysis + CQI (comprehensive)</li>
 * </ul>
 */
public enum AnalysisMode {
    SIMPLE,
    FULL
}

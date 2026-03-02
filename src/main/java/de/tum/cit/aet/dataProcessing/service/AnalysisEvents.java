package de.tum.cit.aet.dataProcessing.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static factory for SSE analysis event maps.
 * Centralises the event type strings so they are defined once and
 * the calling code is free of raw string literals.
 */
final class AnalysisEvents {

    private AnalysisEvents() {
    }

    /** Analysis run started. */
    static Map<String, Object> start(int total, String analysisMode) {
        return Map.of("type", "START", "total", total, "analysisMode", analysisMode);
    }

    /** Analysis run completed successfully. */
    static Map<String, Object> done() {
        return Map.of("type", "DONE");
    }

    /** A new processing phase has begun. */
    static Map<String, Object> phase(String phase, int total) {
        return Map.of("type", "PHASE", "phase", phase, "total", total);
    }

    /** Analysis was cancelled (no counters). */
    static Map<String, Object> cancelled() {
        return Map.of("type", "CANCELLED");
    }

    /** Analysis was cancelled (with progress counters). */
    static Map<String, Object> cancelled(int processed, int total) {
        return Map.of("type", "CANCELLED", "processed", processed, "total", total);
    }

    /** Fatal pipeline error. */
    static Map<String, Object> error(String message) {
        return Map.of("type", "ERROR", "message", message);
    }

    /** A single team failed. */
    static Map<String, Object> errorTeam(Long teamId) {
        return Map.of("type", "ERROR_TEAM", "teamId", teamId);
    }

    /** Progress status update. */
    static Map<String, Object> status(String teamName, String stage, int processed, int total) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "STATUS");
        event.put("processedTeams", processed);
        event.put("totalTeams", total);
        event.put("currentTeamName", teamName);
        event.put("currentStage", stage);
        return event;
    }

    /** Git analysis started for a team. */
    static Map<String, Object> gitAnalyzing(Long teamId, String teamName) {
        return Map.of("type", "GIT_ANALYZING", "teamId", teamId, "teamName", teamName);
    }

    /** All git analysis complete. */
    static Map<String, Object> gitDone(int processed) {
        return Map.of("type", "GIT_DONE", "processed", processed);
    }

    /** Git analysis result for a team. */
    static Map<String, Object> gitUpdate(Object data) {
        return Map.of("type", "GIT_UPDATE", "data", data);
    }

    /** AI/CQI result for a team. */
    static Map<String, Object> aiUpdate(Object data) {
        return Map.of("type", "AI_UPDATE", "data", data);
    }

    /** AI analysis started for a team. */
    static Map<String, Object> aiAnalyzing(Long teamId, String teamName) {
        return Map.of("type", "AI_ANALYZING", "teamId", teamId, "teamName", teamName);
    }

    /** AI analysis failed for a team. */
    static Map<String, Object> aiError(Long teamId, String teamName, String errorMessage) {
        return Map.of("type", "AI_ERROR", "teamId", teamId, "teamName", teamName,
                "error", errorMessage);
    }

    /** Template author detected. */
    static Map<String, Object> templateAuthor(String email, boolean autoDetected) {
        return Map.of("type", "TEMPLATE_AUTHOR", "email", email, "autoDetected", autoDetected);
    }

    /** Ambiguous template author candidates. */
    static Map<String, Object> templateAuthorAmbiguous(List<String> candidates) {
        return Map.of("type", "TEMPLATE_AUTHOR_AMBIGUOUS", "candidates", candidates);
    }

    /** Initial pending team data. */
    static Map<String, Object> init(Map<String, Object> data) {
        return Map.of("type", "INIT", "data", data);
    }
}

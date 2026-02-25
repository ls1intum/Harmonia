package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.artemis.CredentialResolverService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamSummaryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * REST controller and facade for the main analysis pipeline.
 * Provides endpoints for starting analysis, retrieving persisted results,
 * and streaming real-time progress updates via SSE.
 *
 * <p>All heavy lifting is delegated to {@link RequestService}.</p>
 */
@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class RequestResource {

    private final RequestService requestService;
    private final CredentialResolverService credentialResolver;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public RequestResource(RequestService requestService, CredentialResolverService credentialResolver) {
        this.requestService = requestService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * Returns persisted teams for an exercise from the database.
     * Call this first to check if data already exists.
     *
     * @param exerciseId the exercise ID to fetch teams for
     * @return list of team results, empty if no data exists
     */
    @GetMapping("teams/{exerciseId}")
    public ResponseEntity<List<ClientResponseDTO>> getTeamsByExercise(@PathVariable Long exerciseId) {
        log.info("GET teams for exerciseId={}", exerciseId);
        return ResponseEntity.ok(requestService.getTeamsByExerciseId(exerciseId));
    }

    /**
     * Returns lean team summaries for an exercise (no analysisHistory, orphanCommits, etc.).
     * Suitable for the Teams list page initial load.
     *
     * @param exerciseId the exercise ID to fetch summaries for
     * @return list of team summaries, empty if no data exists
     */
    @GetMapping("teams/{exerciseId}/summary")
    public ResponseEntity<List<TeamSummaryDTO>> getTeamSummaries(@PathVariable Long exerciseId) {
        log.info("GET team summaries for exerciseId={}", exerciseId);
        return ResponseEntity.ok(requestService.getTeamSummariesByExerciseId(exerciseId));
    }

    /**
     * Returns full detail for a single team including analysisHistory, orphanCommits, etc.
     * Used for lazy-loading the Team Detail page.
     *
     * @param exerciseId the exercise ID
     * @param teamId     the Artemis team ID
     * @return full team detail, or 404 if not found
     */
    @GetMapping("team/{exerciseId}/{teamId}")
    public ResponseEntity<ClientResponseDTO> getTeamDetail(
            @PathVariable Long exerciseId, @PathVariable Long teamId) {
        log.info("GET team detail for exerciseId={}, teamId={}", exerciseId, teamId);
        return requestService.getTeamDetail(exerciseId, teamId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Checks whether analyzed data (with CQI scores) exists for an exercise.
     *
     * @param exerciseId the exercise ID to check
     * @return true if at least one team has a CQI value
     */
    @GetMapping("hasData/{exerciseId}")
    public ResponseEntity<Boolean> hasAnalyzedData(@PathVariable Long exerciseId) {
        log.info("GET hasData for exerciseId={}", exerciseId);
        return ResponseEntity.ok(requestService.hasAnalyzedDataForExercise(exerciseId));
    }

    /**
     * Starts the analysis pipeline and streams progress via Server-Sent Events.
     * This is the main entry point for triggering a new analysis.
     *
     * <p>The pipeline runs in phases depending on analysisMode:
     * <ul>
     *   <li>{@code SIMPLE} — Clone repos, git analysis, git-only CQI</li>
     *   <li>{@code FULL} — Clone repos, git analysis, AI analysis + full CQI</li>
     * </ul>
     *
     * @param exerciseId        exercise ID to analyze
     * @param analysisMode      analysis mode: SIMPLE or FULL (default: FULL)
     * @param jwtToken          JWT token from cookie
     * @param serverUrl         Artemis server URL from cookie
     * @param username          Artemis username from cookie
     * @param encryptedPassword encrypted Artemis password from cookie
     * @return SSE emitter for streaming progress events
     */
    @GetMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysis(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @RequestParam(value = "analysisMode", defaultValue = "FULL") String analysisMode,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword) {
        log.info("GET streamAnalysis for exerciseId={}, mode={}", exerciseId, analysisMode);

        AnalysisMode mode;
        try {
            mode = AnalysisMode.valueOf(analysisMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = AnalysisMode.FULL;
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 1) Resolve and validate credentials
        ArtemisCredentials credentials = credentialResolver.resolve(jwtToken, serverUrl, username, encryptedPassword);
        if (!credentials.isValid()) {
            log.warn("Authentication required for stream analysis (exerciseId={})", exerciseId);
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        // 2) Check if analysis is already running
        if (requestService.isTaskRunning(exerciseId)) {
            log.info("Analysis already running for exerciseId={}", exerciseId);
            try {
                emitter.send(Map.of("type", "ALREADY_RUNNING", "message", "Analysis is already in progress"));
                emitter.complete();
            } catch (Exception e) {
                log.debug("Could not send ALREADY_RUNNING message: {}", e.getMessage());
            }
            return emitter;
        }

        // 3) Submit analysis pipeline in a background thread
        final AnalysisMode finalMode = mode;
        Future<?> future = executorService.submit(() -> {
            try {
                requestService.fetchAnalyzeAndSaveRepositoriesStream(credentials, exerciseId, finalMode, event -> {
                    try {
                        emitter.send(event);
                    } catch (IllegalStateException e) {
                        log.debug("Client disconnected for exerciseId={}, analysis continues in background", exerciseId);
                    } catch (Exception e) {
                        if (isBrokenPipeException(e)) {
                            log.debug("Client connection closed for exerciseId={}, analysis continues in background", exerciseId);
                        } else {
                            log.error("Error sending SSE event for exerciseId={}", exerciseId, e);
                            try {
                                emitter.completeWithError(e);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Stream processing failed for exerciseId={}", exerciseId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            } finally {
                requestService.unregisterRunningTask(exerciseId);
            }
        });

        // 4) Track the task for cancellation support
        requestService.registerRunningTask(exerciseId, future);

        return emitter;
    }

    /**
     * Synchronous endpoint that fetches, analyzes, and saves repository data.
     * Prefer the streaming endpoint {@link #streamAnalysis} for new integrations.
     *
     * @param exerciseId        the exercise ID to analyze
     * @param jwtToken          JWT token from cookie
     * @param serverUrl         Artemis server URL from cookie
     * @param username          Artemis username from cookie
     * @param encryptedPassword encrypted Artemis password from cookie
     * @return list of analyzed team results
     */
    @GetMapping("fetchData")
    public ResponseEntity<List<ClientResponseDTO>> fetchData(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword) {
        log.info("GET fetchData for exerciseId={}", exerciseId);

        // 1) Resolve and validate credentials
        ArtemisCredentials credentials = credentialResolver.resolve(jwtToken, serverUrl, username, encryptedPassword);
        if (!credentials.isValid()) {
            log.warn("Authentication required for fetchData (exerciseId={})", exerciseId);
            return ResponseEntity.status(401).build();
        }

        // 2) Run synchronous analysis pipeline
        requestService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId);
        return ResponseEntity.ok(requestService.getTeamsByExerciseId(exerciseId));
    }

    /**
     * PATCH endpoint to toggle the review status of a team.
     *
     * @param exerciseId The exercise ID
     * @param teamId     The team ID
     * @return ResponseEntity containing the updated ClientResponseDTO
     */
    @PatchMapping("{exerciseId}/teams/{teamId}/reviewed")
    public ResponseEntity<ClientResponseDTO> toggleReviewStatus(
            @PathVariable Long exerciseId,
            @PathVariable Long teamId) {
        log.info("PATCH request received: toggleReviewStatus for exerciseId: {}, teamId: {}", exerciseId, teamId);
        ClientResponseDTO updated = requestService.toggleReviewStatus(exerciseId, teamId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Returns already-analyzed data from the database without re-analyzing.
     *
     * @param exerciseId the exercise ID to fetch data for
     * @return list of analyzed team results
     */
    @GetMapping("{exerciseId}/getData")
    public ResponseEntity<List<ClientResponseDTO>> getData(@PathVariable Long exerciseId) {
        log.info("GET getData for exerciseId={}", exerciseId);
        return ResponseEntity.ok(requestService.getTeamsByExerciseId(exerciseId));
    }

    /**
     * Checks if an exception is caused by a broken pipe / client disconnect.
     */
    private boolean isBrokenPipeException(Throwable e) {
        while (e != null) {
            String message = e.getMessage();
            if (message != null && (message.contains("Broken pipe") ||
                    message.contains("Connection reset") ||
                    message.contains("ClientAbortException") ||
                    message.contains("AsyncRequestNotUsableException"))) {
                return true;
            }
            if (e.getClass().getSimpleName().contains("ClientAbortException") ||
                    e.getClass().getSimpleName().contains("AsyncRequestNotUsableException")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}

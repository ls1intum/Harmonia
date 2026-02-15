package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.dataProcessing.util.CredentialUtils;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * REST controller for repository analysis requests.
 * Step 1: Provides endpoints for starting analysis, retrieving data, and
 * streaming results.
 */
@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class RequestResource {

    private final RequestService requestService;
    private final CryptoService cryptoService;
    private final ArtemisConfig artemisConfig;
    private final ExecutorService executorService = Executors.newCachedThreadPool();


    @Autowired
    public RequestResource(RequestService requestService, CryptoService cryptoService, ArtemisConfig artemisConfig) {
        this.requestService = requestService;
        this.cryptoService = cryptoService;
        this.artemisConfig = artemisConfig;
    }

    /**
     * Step 1: GET endpoint to fetch teams for an exercise from database.
     * This should be called first to check if data already exists.
     *
     * @param exerciseId The exercise ID to fetch teams for
     * @return ResponseEntity containing the list of ClientResponseDTO, empty if no
     *         data
     */
    @GetMapping("teams/{exerciseId}")
    public ResponseEntity<List<ClientResponseDTO>> getTeamsByExercise(@PathVariable Long exerciseId) {
        log.info("GET request received: getTeamsByExercise for exerciseId: {}", exerciseId);
        List<ClientResponseDTO> teams = requestService.getTeamsByExerciseId(exerciseId);
        return ResponseEntity.ok(teams);
    }

    /**
     * Step 2: GET endpoint to check if analyzed data exists for an exercise.
     *
     * @param exerciseId The exercise ID to check
     * @return ResponseEntity with true if analyzed data exists, false otherwise
     */
    @GetMapping("hasData/{exerciseId}")
    public ResponseEntity<Boolean> hasAnalyzedData(@PathVariable Long exerciseId) {
        log.info("GET request received: hasAnalyzedData for exerciseId: {}", exerciseId);
        boolean hasData = requestService.hasAnalyzedDataForExercise(exerciseId);
        return ResponseEntity.ok(hasData);
    }

    /**
     * Step 3: Endpoint for streaming repository analysis results using Server-Sent
     * Events.
     * This is the main entry point for starting a new analysis with real-time
     * updates.
     *
     * @param exerciseId        Exercise ID to analyze
     * @param jwtToken          JWT token from cookie
     * @param serverUrl         Artemis server URL from cookie
     * @param username          Artemis username from cookie
     * @param encryptedPassword Encrypted Artemis password from cookie
     * @return SSE emitter for streaming results
     */
    @GetMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysis(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword) {
        log.info("GET request received: streamAnalysis for exerciseId: {}", exerciseId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // No timeout

        String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        if (!credentials.isValid()) {
            log.warn("No credentials found in cookies. Authentication required.");
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        // Check if analysis is already running for this exercise
        if (requestService.isTaskRunning(exerciseId)) {
            log.info("Analysis already running for exercise {}, client can poll status", exerciseId);
            // Send a message that analysis is already running, then complete
            // The client should poll the status endpoint instead
            try {
                emitter.send(java.util.Map.of("type", "ALREADY_RUNNING", "message", "Analysis is already in progress"));
                emitter.complete();
            } catch (Exception e) {
                log.debug("Could not send ALREADY_RUNNING message: {}", e.getMessage());
            }
            return emitter;
        }

        Future<?> future = executorService.submit(() -> {
            try {
                requestService.fetchAnalyzeAndSaveRepositoriesStream(credentials, exerciseId, (event) -> {
                    try {
                        emitter.send(event);
                    } catch (IllegalStateException e) {
                        // Client disconnected - this is normal when user navigates away or refreshes
                        // DON'T stop the analysis - let it continue in the background
                        // The client can reconnect and get the current status
                        log.debug("Client disconnected for exercise {}, analysis continues in background", exerciseId);
                    } catch (Exception e) {
                        // Check if this is a broken pipe / client abort
                        if (isBrokenPipeException(e)) {
                            // DON'T stop the analysis - let it continue in the background
                            log.debug("Client connection closed for exercise {}, analysis continues in background", exerciseId);
                        } else {
                            log.error("Error sending SSE event for exercise {}", exerciseId, e);
                            try {
                                emitter.completeWithError(e);
                            } catch (Exception ignored) {
                                // Emitter may already be completed
                            }
                        }
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Error in stream processing for exercise {}", exerciseId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // Emitter may already be completed
                }
            } finally {
                // Remove from tracking when done
                requestService.unregisterRunningTask(exerciseId);
            }
        });

        // Track this task in RequestService
        requestService.registerRunningTask(exerciseId, future);

        return emitter;
    }


    /**
     * GET endpoint to fetch, analyze, and save repository data (synchronous).
     * This is the legacy endpoint - prefer using stream endpoint for new calls.
     *
     * @param exerciseId        The exercise ID to fetch participations for
     * @param jwtToken          The JWT token from the cookie
     * @param serverUrl         The Artemis server URL from the cookie
     * @param username          The Artemis username from the cookie
     * @param encryptedPassword The encrypted Artemis password from the cookie
     * @return ResponseEntity containing the list of ClientResponseDTO
     */
    @GetMapping("fetchData")
    public ResponseEntity<List<ClientResponseDTO>> fetchData(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword) {
        log.info("GET request received: fetchData for exerciseId: {}", exerciseId);

        String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        // Fallback to config if cookies are missing
        if (!credentials.isValid()) {
            log.info("No credentials in cookies, using config values");
            credentials = new ArtemisCredentials(
                    artemisConfig.getBaseUrl(),
                    artemisConfig.getJwtToken(),
                    artemisConfig.getUsername(),
                    artemisConfig.getPassword());

            if (!credentials.isValid()) {
                log.warn("No valid credentials found. Authentication required.");
                return ResponseEntity.status(401).build();
            }
        }

        requestService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId);
        List<ClientResponseDTO> clientResponseDTOS = requestService.getTeamsByExerciseId(exerciseId);
        return ResponseEntity.ok(clientResponseDTOS);
    }

    /**
     * GET endpoint to retrieve already-analyzed data from database without
     * re-analyzing, filtered by exercise ID.
     *
     * @param exerciseId the Artemis exercise ID to filter by
     * @return ResponseEntity containing the list of ClientResponseDTO
     */
    @GetMapping("{exerciseId}/getData")
    public ResponseEntity<List<ClientResponseDTO>> getData(@PathVariable Long exerciseId) {
        log.info("GET request received: getData (from database) for exercise {}", exerciseId);
        List<ClientResponseDTO> clientResponseDTOS = requestService.getTeamsByExerciseId(exerciseId);
        return ResponseEntity.ok(clientResponseDTOS);
    }

    /**
     * Check if an exception is caused by a broken pipe / client disconnect.
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

package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.dataProcessing.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.*;

@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class StreamResource {

    private final RequestService requestService;
    private final CryptoService cryptoService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Track running tasks per exercise ID so they can be cancelled
    private final ConcurrentHashMap<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();

    @Autowired
    public StreamResource(RequestService requestService, CryptoService cryptoService) {
        this.requestService = requestService;
        this.cryptoService = cryptoService;
    }

    /**
     * Endpoint for streaming repository analysis results using Server-Sent Events.
     *
     * @param exerciseId Exercise ID to analyze
     * @param jwtToken JWT token from cookie
     * @param serverUrl Artemis server URL from cookie
     * @param username Artemis username from cookie
     * @param encryptedPassword Encrypted Artemis password from cookie
     * @return SSE emitter for streaming results
     */
    @GetMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamData(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword
    ) {
        log.info("GET request received: streamData for exerciseId: {}", exerciseId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // No timeout

        String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        if (!credentials.isValid()) {
            log.warn("No credentials found in cookies. Authentication required.");
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        // Cancel any existing task for this exercise
        cancelRunningTask(exerciseId);

        Future<?> future = executorService.submit(() -> {
            try {
                requestService.fetchAnalyzeAndSaveRepositoriesStream(credentials, exerciseId, (event) -> {
                    try {
                        emitter.send(event);
                    } catch (Exception e) {
                        log.error("Error sending SSE event", e);
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Error in stream processing", e);
                emitter.completeWithError(e);
            } finally {
                // Remove from tracking when done
                runningTasks.remove(exerciseId);
            }
        });

        // Track this task
        runningTasks.put(exerciseId, future);

        return emitter;
    }

    /**
     * Cancel a running analysis task for the given exercise.
     * This will interrupt the thread pool workers.
     * @param exerciseId the ID of the exercise
     */
    public void cancelRunningTask(Long exerciseId) {
        Future<?> task = runningTasks.remove(exerciseId);
        if (task != null && !task.isDone()) {
            log.info("Cancelling running task for exercise {}", exerciseId);
            task.cancel(true); // true = interrupt if running
        }
    }
}

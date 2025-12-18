package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class StreamResource {

    private final RequestService requestService;
    private final CryptoService cryptoService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Autowired
    public StreamResource(RequestService requestService, CryptoService cryptoService) {
        this.requestService = requestService;
        this.cryptoService = cryptoService;
    }

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

        String password = decryptPassword(encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        if (!credentials.isValid()) {
            log.warn("No credentials found in cookies. Authentication required.");
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        executorService.execute(() -> {
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
            }
        });

        return emitter;
    }

    private String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null) {
            return null;
        }
        try {
            return cryptoService.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.error("Failed to decrypt password from cookie", e);
            return null;
        }
    }
}

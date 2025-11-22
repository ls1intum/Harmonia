package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.exceptions.ArtemisConnectionException;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for communicating with the Artemis API.
 * Handles authentication and fetching of participation data.
 */
@Service
@Slf4j
public class ArtemisClientService {

    private final ArtemisConfig artemisConfig;
    private final RestClient restClient;

    @Autowired
    public ArtemisClientService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
        restClient = RestClient.builder()
                .baseUrl(artemisConfig.getBaseUrl())
                .defaultHeader("Cookie", "jwt=" + artemisConfig.getJwtToken())
                .build();
    }

    /**
     * Authenticates with the Artemis server and retrieves the JWT cookie.
     *
     * @param serverUrl The Artemis server URL
     * @param username  The username
     * @param password  The password
     * @return The JWT cookie string
     */
    public String authenticate(String serverUrl, String username, String password) {
        return authenticateInternal(serverUrl, username, password, 0);
    }

    private String authenticateInternal(String serverUrl, String username, String password, int retryCount) {
        if (retryCount > 3) {
            throw new ArtemisConnectionException("Too many redirects during authentication");
        }

        log.info("Authenticating with Artemis at {} (attempt {})", serverUrl, retryCount + 1);

        var authRequest = Map.of(
                "username", username,
                "password", password,
                "rememberMe", true
        );

        try {
            // Ensure we hit the correct endpoint structure
            // Modern Artemis uses /api/core/public/authenticate
            // We try to construct the path carefully
            String authPath = "/api/core/public/authenticate";
            
            // If the serverUrl already ends with /api, we should avoid doubling it if we were using /api/...
            // But here we assume serverUrl is the root (e.g. https://artemis.tum.de)
            
            ResponseEntity<String> response = RestClient.create(serverUrl).post()
                    .uri(authPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(authRequest)
                    .retrieve()
                    .toEntity(String.class);

            log.info("Artemis Auth Response Status: {}", response.getStatusCode());
            
            if (response.getStatusCode().is3xxRedirection()) {
                String newLocation = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (newLocation != null) {
                    log.info("Redirect detected to: {}", newLocation);
                    
                    // Normalize new location to be a base URL
                    String newBaseUrl = newLocation;
                    if (newBaseUrl.endsWith(authPath)) {
                        newBaseUrl = newBaseUrl.substring(0, newBaseUrl.length() - authPath.length());
                    }
                    if (newBaseUrl.endsWith("/")) {
                        newBaseUrl = newBaseUrl.substring(0, newBaseUrl.length() - 1);
                    }
                    
                    return authenticateInternal(newBaseUrl, username, password, retryCount + 1);
                }
            }

            log.info("Artemis Auth Response Headers: {}", response.getHeaders());
            log.info("Artemis Auth Response Body: {}", response.getBody());

            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies != null) {
                for (String cookie : cookies) {
                    if (cookie.startsWith("jwt=")) {
                        int start = 4;
                        int end = cookie.indexOf(';');
                        if (end == -1) {
                            end = cookie.length();
                        }
                        return cookie.substring(start, end);
                    }
                }
            }

            throw new ArtemisConnectionException("No JWT cookie received from Artemis");
        } catch (ArtemisConnectionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication failed", e);
            throw new ArtemisConnectionException("Authentication failed", e);
        }
    }

    /**
     * Fetches all participations for the configured exercise from Artemis.
     *
     * @return List of participation DTOs containing team and repository information
     */
    public List<ParticipationDTO> fetchParticipations() {
        log.info("Fetching participations for exercise ID: {}", artemisConfig.getExerciseId());

        String uri = String.format("/exercise/exercises/%d/participations?withLatestResults=false",
                artemisConfig.getExerciseId());

        try {
            List<ParticipationDTO> participations = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            log.info("Successfully fetched {} participations",
                    participations != null ? participations.size() : 0);

            return participations;

        } catch (Exception e) {
            log.error("Error fetching participations from Artemis", e);
            throw new ArtemisConnectionException("Failed to fetch participations from Artemis", e);
        }
    }
}

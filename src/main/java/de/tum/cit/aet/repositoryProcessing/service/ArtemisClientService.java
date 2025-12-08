package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.exceptions.ArtemisConnectionException;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
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

    @Autowired
    public ArtemisClientService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
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

        // Strip /api suffix if present
        if (serverUrl.endsWith("/api")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 4);
        } else if (serverUrl.endsWith("/api/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 5);
        }

        log.info("Authenticating with Artemis at {} (attempt {})", serverUrl, retryCount + 1);

        Map<String, Object> authRequest = Map.of(
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

            if (response.getStatusCode().is3xxRedirection()) {
                String newLocation = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (newLocation != null) {
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
        } catch (Exception e) {
            log.error("Authentication failed", e);
            throw new ArtemisConnectionException("Authentication failed", e);
        }
    }

    /**
     * Fetches all participations for the configured exercise from Artemis using provided credentials.
     *
     * @param serverUrl The Artemis server URL
     * @param jwtToken  The JWT token for authentication
     * @return List of participation DTOs containing team and repository information
     */
    public List<ParticipationDTO> fetchParticipations(String serverUrl, String jwtToken) {
        log.info("Fetching participations for exercise ID: {} from {}", artemisConfig.getExerciseId(), serverUrl);

        String uri = String.format("/api/exercise/exercises/%d/participations?withLatestResults=false",
                artemisConfig.getExerciseId());

        try {
            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            List<ParticipationDTO> participations = dynamicClient.get()
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

    /**
     * Fetches a VCS access token for a specific participation.
     *
     * @param serverUrl       The Artemis server URL
     * @param jwtToken        The JWT token for authentication
     * @param participationId The ID of the participation
     * @return The VCS access token
     */
    public String getVcsAccessToken(String serverUrl, String jwtToken, Long participationId) {
        String uri = "/api/core/account/participation-vcs-access-token?participationId=" + participationId;

        try {
            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            // Assuming GET as POST failed with 405
            String vcsToken = dynamicClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            return vcsToken;

        } catch (Exception e) {
            log.error("Error fetching VCS access token for participation {}", participationId, e);
            throw new ArtemisConnectionException("Failed to fetch VCS access token", e);
        }
    }

    /**
     * Fetches the VCS access log for a specific participation.
     *
     * @param serverUrl       The Artemis server URL
     * @param jwtToken        The JWT token for authentication
     * @param participationId The ID of the participation
     * @return List of VCS log DTOs filtered for commits
     */
    public List<VCSLogDTO> fetchVCSAccessLog(String serverUrl, String jwtToken, Long participationId) {
        log.info("Fetching VCS access log for participation ID: {}", participationId);

        String uri = String.format("/api/programming/programming-exercise-participations/%d/vcs-access-log", participationId);
        try {

            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            List<VCSLogDTO> vcsLogs = dynamicClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            // Filter the fetched logs to only include "WRITE" actions, indicating a commit
            if (vcsLogs != null) {
                vcsLogs = vcsLogs.stream()
                        .filter(entry -> "WRITE".equals(entry.repositoryActionType()))
                        .toList();
            }

            return vcsLogs;
        } catch (Exception e) {
            log.error("Error fetching participations from Artemis", e);
            throw new ArtemisConnectionException("Failed to fetch VCS access logs from Artemis", e);
        }
    }
}

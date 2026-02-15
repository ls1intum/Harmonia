package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.exceptions.ArtemisConnectionException;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for communicating with the Artemis API.
 * Handles authentication and fetching of participation data.
 */
@Service
@Slf4j
public class ArtemisClientService {

    @SuppressWarnings("unused")
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
     * @param serverUrl  The Artemis server URL
     * @param jwtToken   The JWT token for authentication
     * @param exerciseId The exercise ID to fetch participations for
     * @return List of participation DTOs containing team and repository information
     */
    public List<ParticipationDTO> fetchParticipations(String serverUrl, String jwtToken, Long exerciseId) {
        log.info("Fetching participations for exercise ID: {} from {}", exerciseId, serverUrl);

        String uri = String.format("/api/exercise/exercises/%d/participations?withLatestResults=false",
                exerciseId);

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
        log.info("Fetching VCS access log for participation ID: {} from {}", participationId, serverUrl + "/api/programming/programming-exercise-participations/" + participationId + "/vcs-access-log");

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

            log.info("Raw VCS logs received: {} entries", vcsLogs != null ? vcsLogs.size() : 0);
            if (vcsLogs != null && !vcsLogs.isEmpty()) {
                log.info("Sample VCS log entry: {}", vcsLogs.get(0));
                // Log all unique action types
                List<String> actionTypes = vcsLogs.stream()
                        .map(VCSLogDTO::repositoryActionType)
                        .distinct()
                        .toList();
                log.info("Action types found: {}", actionTypes);
            }

            // Filter the fetched logs
            if (vcsLogs != null) {
                Set<String> validActions = Set.of("WRITE", "PUSH");
                int beforeFilter = vcsLogs.size();
                vcsLogs = vcsLogs.stream()
                        .filter(entry -> validActions.contains(entry.repositoryActionType()))
                        .toList();
                log.info("After filtering for WRITE actions: {} entries (was {})", vcsLogs.size(), beforeFilter);
            }

            return vcsLogs;
        } catch (Exception e) {
            log.error("Error fetching VCS access log from Artemis for participation {}", participationId, e);
            throw new ArtemisConnectionException("Failed to fetch VCS access logs from Artemis", e);
        }
    }

    /**
     * Verifies whether the provided username belongs to the list of instructors for the given course.
     * Calls the Artemis instructors endpoint and inspects both JSON and XML responses.
     *
     * @param serverUrl The base Artemis server URL (scheme+host)
     * @param jwtToken  The JWT token used as cookie (without the "jwt=" prefix)
     * @param courseId  The numeric course id to check
     * @param username  The username to verify
     * @return true if username is present in the instructors list, false otherwise
     */
    public boolean isUserInstructor(String serverUrl, String jwtToken, Long courseId, String username) {
        log.info("Verifying instructor {} for course {} at {}", username, courseId, serverUrl);
        String uri = String.format("/api/core/courses/%d/instructors", courseId);
        try {
            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            ResponseEntity<String> response = dynamicClient.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Instructor check returned non-2xx status: {}", response.getStatusCode().value());
                return false;
            }

            String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            String body = response.getBody();
            if (body == null) {
                return false;
            }
            if (contentType != null && contentType.contains("application/json")) {
                // Check common representations: "login":"username" or "login": "username"
                return body.contains("\"login\":\"" + username + "\"") || body.contains("\"login\": \"" + username + "\"");
            } else {
                // Try simple substring match for <login>username</login>
                return body.contains("<login>" + username + "</login>") || body.contains("\"login\": \"" + username + "\"");
            }
        } catch (Exception e) {
            log.info("Error while verifying instructor membership", e);
            return false;
        }
    }

    /**
     * Fetches the submission deadline for a given exercise
     *
     * @param serverUrl The base Artemis server URL (scheme+host)
     * @param jwtToken  The JWT token used as cookie (without the "jwt=" prefix)
     * @param exerciseId  The numeric exerciseId id to check
     * @return The deadline of the exercise we're checking
     */
    public OffsetDateTime fetchSubmissionDeadline(
            String serverUrl,
            String jwtToken,
            Long exerciseId
    ) {
        log.info("Fetching submission deadline for exercise {} from {}", exerciseId, serverUrl);

        String uri = String.format("/api/exercise/exercises/%d/latest-due-date", exerciseId);
        try {
            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            JsonNode response = dynamicClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.isNull()) {
                return null;
            }

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(response.asString());
            ZonedDateTime berlinTime = zonedDateTime.withZoneSameInstant(ZoneId.of("Europe/Berlin"));
            log.info("Fetched submission deadline for exercise {} - {}", exerciseId, berlinTime);

            return berlinTime.toOffsetDateTime();


        } catch (Exception e) {
            log.error("Error fetching submission deadline for exercise {}", exerciseId, e);
            throw new ArtemisConnectionException("Failed to fetch tutorial groups", e);
        }
    }

    /**
     * Fetches tutorial groups and returns a mapping from group name to session start times.
     *
     * @param serverUrl The Artemis server URL
     * @param jwtToken  The JWT token for authentication
     * @param courseId  The course ID to fetch tutorial groups for
     * @return Map of tutorial group name to list of session start times
     */
    public Map<String, List<OffsetDateTime>> fetchTutorialGroupSessionStartTimes(
            String serverUrl,
            String jwtToken,
            Long courseId
    ) {
        log.info("Fetching tutorial groups for course {} from {}", courseId, serverUrl);

        String uri = String.format("/api/tutorialgroup/courses/%d/tutorial-groups", courseId);
        try {
            RestClient dynamicClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .defaultHeader("Cookie", "jwt=" + jwtToken)
                    .build();

            JsonNode response = dynamicClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                log.warn("Unexpected tutorial group response shape");
                return Map.of();
            }

            Map<String, List<OffsetDateTime>> result = new HashMap<>();
            for (JsonNode groupNode : response) {
                String groupName = firstNonBlank(groupNode, "title", "name", "tutorialGroupName");
                if (groupName == null || groupName.isBlank()) {
                    continue;
                }

                List<OffsetDateTime> sessions = extractSessionStartTimes(groupNode);
                result.put(groupName, sessions);
            }

            log.info("Fetched {} tutorial groups", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error fetching tutorial groups for course {}", courseId, e);
            throw new ArtemisConnectionException("Failed to fetch tutorial groups", e);
        }
    }

    private List<OffsetDateTime> extractSessionStartTimes(JsonNode groupNode) {
        List<OffsetDateTime> sessions = new ArrayList<>();

        JsonNode sessionsNode = groupNode.get("tutorialGroupSessions");
        if (sessionsNode == null || !sessionsNode.isArray()) {
            sessionsNode = groupNode.get("sessions");
        }
        if ((sessionsNode == null || !sessionsNode.isArray())) {
            JsonNode scheduleNode = groupNode.get("tutorialGroupSchedule");
            if (scheduleNode != null && !scheduleNode.isNull()) {
                sessionsNode = scheduleNode.get("tutorialGroupSessions");
                if (sessionsNode == null || !sessionsNode.isArray()) {
                    sessionsNode = scheduleNode.get("sessions");
                }
            }
        }

        if (sessionsNode == null || !sessionsNode.isArray()) {
            return sessions;
        }

        for (JsonNode sessionNode : sessionsNode) {
            String startValue = firstNonBlank(sessionNode, "start", "session_start");
            if (startValue == null || startValue.isBlank()) {
                continue;
            }
            OffsetDateTime parsed = parseOffsetDateTime(startValue);
            if (parsed != null) {
                sessions.add(parsed);
            }
        }

        return sessions;
    }

    private String firstNonBlank(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.get(fieldName);
            if (valueNode != null && !valueNode.isNull()) {
                String value = valueNode.asString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value);
            return parsed.atZoneSameInstant(ZoneId.of("Europe/Berlin")).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }

    }
}

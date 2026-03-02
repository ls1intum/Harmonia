package de.tum.cit.aet.artemis;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.exceptions.ArtemisConnectionException;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
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
 * Handles authentication, participation fetching, VCS access logs,
 * instructor verification, and tutorial group retrieval.
 */
@Slf4j
@Service
public class ArtemisClientService {

    private static final String AUTH_PATH = "/api/core/public/authenticate";
    private static final int MAX_AUTH_REDIRECTS = 3;
    private static final Set<String> VALID_VCS_ACTIONS = Set.of("WRITE", "PUSH");

    @SuppressWarnings("unused")
    private final ArtemisConfig artemisConfig;

    public ArtemisClientService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
    }

    /**
     * Authenticates with the Artemis server and retrieves the JWT token.
     * Follows up to {@value #MAX_AUTH_REDIRECTS} redirects automatically.
     *
     * @param serverUrl the Artemis server URL
     * @param username  the username
     * @param password  the password
     * @return the JWT token string
     * @throws ArtemisConnectionException if authentication fails or no JWT is returned
     */
    public String authenticate(String serverUrl, String username, String password) {
        return authenticateInternal(normalizeServerUrl(serverUrl), username, password, 0);
    }

    /**
     * Fetches all participations for a given exercise from Artemis.
     *
     * @param serverUrl  the Artemis server URL
     * @param jwtToken   the JWT token for authentication
     * @param exerciseId the exercise ID
     * @return list of participation DTOs
     * @throws ArtemisConnectionException if the request fails
     */
    public List<ParticipationDTO> fetchParticipations(String serverUrl, String jwtToken, Long exerciseId) {
        String uri = String.format("/api/exercise/exercises/%d/participations?withLatestResults=false", exerciseId);

        try {
            List<ParticipationDTO> participations = buildClient(serverUrl, jwtToken).get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return participations;
        } catch (Exception e) {
            throw new ArtemisConnectionException("Failed to fetch participations from Artemis", e);
        }
    }

    /**
     * Fetches the VCS access log for a specific participation and filters for commit actions
     * ({@code WRITE} and {@code PUSH}).
     *
     * @param serverUrl       the Artemis server URL
     * @param jwtToken        the JWT token for authentication
     * @param participationId the participation ID
     * @return filtered list of VCS log DTOs containing only commit-related entries
     * @throws ArtemisConnectionException if the request fails
     */
    public List<VCSLogDTO> fetchVCSAccessLog(String serverUrl, String jwtToken, Long participationId) {
        String uri = String.format("/api/programming/programming-exercise-participations/%d/vcs-access-log", participationId);

        try {
            List<VCSLogDTO> vcsLogs = buildClient(serverUrl, jwtToken).get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (vcsLogs == null) {
                return List.of();
            }

            // Filter for commit-relevant actions only
            return vcsLogs.stream()
                    .filter(entry -> VALID_VCS_ACTIONS.contains(entry.repositoryActionType()))
                    .toList();
        } catch (Exception e) {
            throw new ArtemisConnectionException("Failed to fetch VCS access logs from Artemis", e);
        }
    }

    /**
     * Verifies whether the given username is an instructor for the specified course.
     *
     * @param serverUrl the Artemis server URL
     * @param jwtToken  the JWT token for authentication
     * @param courseId   the course ID
     * @param username  the username to verify
     * @return {@code true} if the user is an instructor, {@code false} otherwise
     */
    public boolean isUserInstructor(String serverUrl, String jwtToken, Long courseId, String username) {
        String uri = String.format("/api/core/courses/%d/instructors", courseId);

        try {
            ResponseEntity<String> response = buildClient(serverUrl, jwtToken).get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }

            return bodyContainsLogin(response.getBody(),
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE), username);
        } catch (Exception e) {
            log.error("Error while verifying instructor membership for user '{}'", username, e);
            return false;
        }
    }

    /**
     * Fetches the submission deadline for a given exercise.
     *
     * @param serverUrl  the Artemis server URL
     * @param jwtToken   the JWT token for authentication
     * @param exerciseId the exercise ID
     * @return the deadline as {@link OffsetDateTime}, or {@code null} if none is set
     * @throws ArtemisConnectionException if the request fails
     */
    public OffsetDateTime fetchSubmissionDeadline(String serverUrl, String jwtToken, Long exerciseId) {
        String uri = String.format("/api/exercise/exercises/%d/latest-due-date", exerciseId);

        try {
            JsonNode response = buildClient(serverUrl, jwtToken).get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.isNull()) {
                return null;
            }

            String dateString = response.asString();
            if (dateString == null || dateString.isBlank()) {
                return null;
            }
            ZonedDateTime berlinTime = ZonedDateTime.parse(dateString)
                    .withZoneSameInstant(ZoneId.of("Europe/Berlin"));
            return berlinTime.toOffsetDateTime();
        } catch (Exception e) {
            throw new ArtemisConnectionException("Failed to fetch submission deadline", e);
        }
    }

    /**
     * Fetches tutorial groups for a course and returns a mapping from group name to sessions.
     *
     * @param serverUrl the Artemis server URL
     * @param jwtToken  the JWT token for authentication
     * @param courseId   the course ID
     * @return map of tutorial group name to its sessions
     * @throws ArtemisConnectionException if the request fails
     */
    public Map<String, List<TutorialGroupSessionDTO>> fetchTutorialGroupSessions(
            String serverUrl, String jwtToken, Long courseId) {
        String uri = String.format("/api/tutorialgroup/courses/%d/tutorial-groups", courseId);

        try {
            JsonNode response = buildClient(serverUrl, jwtToken).get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                return Map.of();
            }

            Map<String, List<TutorialGroupSessionDTO>> result = new HashMap<>();
            for (JsonNode groupNode : response) {
                String groupName = firstNonBlank(groupNode, "title", "name", "tutorialGroupName");
                if (groupName == null || groupName.isBlank()) {
                    continue;
                }
                result.put(groupName, extractSessionInfos(groupNode));
            }

            return result;
        } catch (Exception e) {
            throw new ArtemisConnectionException("Failed to fetch tutorial groups", e);
        }
    }

    // ---- Private helpers ----

    private String authenticateInternal(String serverUrl, String username, String password, int retryCount) {
        if (retryCount > MAX_AUTH_REDIRECTS) {
            throw new ArtemisConnectionException("Too many redirects during authentication");
        }

        Map<String, Object> authRequest = Map.of(
                "username", username,
                "password", password,
                "rememberMe", true
        );

        try {
            ResponseEntity<String> response = RestClient.create(serverUrl).post()
                    .uri(AUTH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(authRequest)
                    .retrieve()
                    .toEntity(String.class);

            // 1) Follow redirects if necessary
            if (response.getStatusCode().is3xxRedirection()) {
                String newLocation = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (newLocation != null) {
                    String newBaseUrl = normalizeRedirectUrl(newLocation);
                    return authenticateInternal(newBaseUrl, username, password, retryCount + 1);
                }
            }

            // 2) Extract JWT from Set-Cookie header
            return extractJwtFromCookies(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        } catch (ArtemisConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtemisConnectionException("Authentication failed", e);
        }
    }

    private String extractJwtFromCookies(List<String> cookies) {
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("jwt=")) {
                    int end = cookie.indexOf(';');
                    return cookie.substring(4, end == -1 ? cookie.length() : end);
                }
            }
        }
        throw new ArtemisConnectionException("No JWT cookie received from Artemis");
    }

    private String normalizeServerUrl(String serverUrl) {
        if (serverUrl.endsWith("/api/")) {
            return serverUrl.substring(0, serverUrl.length() - 5);
        }
        if (serverUrl.endsWith("/api")) {
            return serverUrl.substring(0, serverUrl.length() - 4);
        }
        return serverUrl;
    }

    private String normalizeRedirectUrl(String location) {
        if (location.endsWith(AUTH_PATH)) {
            location = location.substring(0, location.length() - AUTH_PATH.length());
        }
        if (location.endsWith("/")) {
            location = location.substring(0, location.length() - 1);
        }
        return location;
    }

    private boolean bodyContainsLogin(String body, String contentType, String username) {
        if (contentType != null && contentType.contains("application/json")) {
            return body.contains("\"login\":\"" + username + "\"")
                    || body.contains("\"login\": \"" + username + "\"");
        }
        return body.contains("<login>" + username + "</login>")
                || body.contains("\"login\": \"" + username + "\"");
    }

    private RestClient buildClient(String serverUrl, String jwtToken) {
        return RestClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader("Cookie", "jwt=" + jwtToken)
                .build();
    }

    private List<TutorialGroupSessionDTO> extractSessionInfos(JsonNode groupNode) {
        List<TutorialGroupSessionDTO> sessions = new ArrayList<>();

        JsonNode sessionsNode = findSessionsNode(groupNode);
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
                sessions.add(new TutorialGroupSessionDTO(
                        null, parsed, null, null, isSessionCancelled(sessionNode)));
            }
        }

        return sessions;
    }

    private JsonNode findSessionsNode(JsonNode groupNode) {
        JsonNode sessionsNode = groupNode.get("tutorialGroupSessions");
        if (sessionsNode != null && sessionsNode.isArray()) {
            return sessionsNode;
        }

        sessionsNode = groupNode.get("sessions");
        if (sessionsNode != null && sessionsNode.isArray()) {
            return sessionsNode;
        }

        JsonNode scheduleNode = groupNode.get("tutorialGroupSchedule");
        if (scheduleNode != null && !scheduleNode.isNull()) {
            sessionsNode = scheduleNode.get("tutorialGroupSessions");
            if (sessionsNode != null && sessionsNode.isArray()) {
                return sessionsNode;
            }
            return scheduleNode.get("sessions");
        }

        return null;
    }

    private boolean isSessionCancelled(JsonNode sessionNode) {
        String status = firstNonBlank(sessionNode, "status");
        return "CANCELLED".equalsIgnoreCase(status);
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
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.of("Europe/Berlin"))
                    .toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

package de.tum.cit.aet.pairProgramming.web;

import de.tum.cit.aet.artemis.CredentialResolverService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.service.PairProgrammingMetricsService;
import de.tum.cit.aet.pairProgramming.dto.TeamsScheduleDTO;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for attendance upload and pair programming management.
 */
@RestController
@RequestMapping("api/attendance")
@Slf4j
public class PairProgrammingResource {

    private final PairProgrammingService pairProgrammingService;
    private final PairProgrammingMetricsService metricsService;
    private final CredentialResolverService credentialResolver;

    public PairProgrammingResource(PairProgrammingService pairProgrammingService,
                                   PairProgrammingMetricsService metricsService,
                                   CredentialResolverService credentialResolver) {
        this.pairProgrammingService = pairProgrammingService;
        this.metricsService = metricsService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * Uploads and processes attendance data from an Excel file.
     *
     * @param file              the Excel file to upload
     * @param courseId          the course ID
     * @param exerciseId        the exercise ID
     * @param jwtToken          the JWT token from cookies
     * @param serverUrl         the Artemis server URL from cookies
     * @param username          the username from cookies
     * @param encryptedPassword the encrypted password from cookies
     * @param requestServerUrl  optional Artemis server URL passed in the request
     * @param requestUsername   optional Artemis username passed in the request
     * @param requestPassword   optional Artemis password passed in the request
     * @return the teams schedule DTO
     */
    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TeamsScheduleDTO> uploadAttendance(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseId") Long courseId,
            @RequestParam("exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword,
            @RequestParam(value = "serverUrl", required = false) String requestServerUrl,
            @RequestParam(value = "username", required = false) String requestUsername,
            @RequestParam(value = "password", required = false) String requestPassword
    ) {
        log.info("POST uploadAttendance for exerciseId={}, courseId={}", exerciseId, courseId);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 1) Resolve credentials (request params > cookies > config)
        ArtemisCredentials credentials = credentialResolver.resolve(
                jwtToken, serverUrl, username, encryptedPassword,
                requestServerUrl, requestUsername, requestPassword);

        if (!credentials.isValid()) {
            log.warn("Authentication required for uploadAttendance (exerciseId={})", exerciseId);
            return ResponseEntity.status(401).build();
        }

        // 2) Parse attendance and trigger async recomputation
        TeamsScheduleDTO results = pairProgrammingService.parseAttendance(file, credentials, courseId, exerciseId);
        metricsService.recomputeForExerciseAsync(exerciseId);

        return ResponseEntity.ok(results);
    }

    /**
     * Clears uploaded attendance data and removes pair programming metrics for an exercise.
     * Supports both DELETE and POST for environments where DELETE is restricted.
     *
     * @param exerciseId the exercise ID
     * @return a success response
     */
    @RequestMapping(value = "clear", method = {RequestMethod.DELETE, RequestMethod.POST})
    public ResponseEntity<String> clearAttendance(@RequestParam("exerciseId") Long exerciseId) {
        pairProgrammingService.clear();
        int updatedTeams = metricsService.clearPairProgrammingForExercise(exerciseId);
        log.info("DELETE clearAttendance for exerciseId={}, teamsUpdated={}", exerciseId, updatedTeams);
        return ResponseEntity.ok("Attendance cleared successfully");
    }
}

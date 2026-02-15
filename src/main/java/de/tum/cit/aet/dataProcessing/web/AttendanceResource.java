package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import de.tum.cit.aet.dataProcessing.service.AttendanceService;
import de.tum.cit.aet.dataProcessing.util.CredentialUtils;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("api/attendance")
@Slf4j
public class AttendanceResource {
    /**
     * Passes the Excel file information to the Attendance Service
     */

    private final AttendanceService attendanceService;
    private final CryptoService cryptoService;
    private final ArtemisConfig artemisConfig;
    private final ArtemisClientService artemisClientService;

    /**
     * Constructs the AttendanceResource with required dependencies.
     *
     * @param attendanceService the attendance service
     * @param cryptoService the crypto service
     * @param artemisConfig the Artemis configuration
     * @param artemisClientService the Artemis client service
     */
    public AttendanceResource(
            AttendanceService attendanceService,
            CryptoService cryptoService,
            ArtemisConfig artemisConfig,
            ArtemisClientService artemisClientService
    ) {
        this.attendanceService = attendanceService;
        this.cryptoService = cryptoService;
        this.artemisConfig = artemisConfig;
        this.artemisClientService = artemisClientService;
    }

    /**
     * Uploads and processes attendance data from an Excel file.
     *
     * @param file the Excel file to upload
     * @param courseId the course ID
     * @param jwtToken the JWT token from cookies
     * @param serverUrl the Artemis server URL from cookies
     * @param username the username from cookies
     * @param encryptedPassword the encrypted password from cookies
     * @param requestServerUrl optional Artemis server URL passed in the request
     * @param requestUsername optional Artemis username passed in the request
     * @param requestPassword optional Artemis password passed in the request
     * @param exerciseId the exercise ID
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
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ArtemisCredentials credentials = null;

        // Priority 1: Request credentials (most current - user just entered these)
        if (StringUtils.hasText(requestServerUrl) && StringUtils.hasText(requestUsername) && StringUtils.hasText(requestPassword)) {
            log.info("Using request credentials for attendance upload");
            try {
                String requestJwt = artemisClientService.authenticate(requestServerUrl, requestUsername, requestPassword);
                credentials = new ArtemisCredentials(requestServerUrl, requestJwt, requestUsername, requestPassword);
            } catch (Exception e) {
                log.warn("Request credential authentication failed: {}", e.getMessage());
            }
        }

        // Priority 2: Cookie credentials (may be stale or for different course)
        if (credentials == null || !credentials.isValid()) {
            String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
            ArtemisCredentials cookieCredentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);
            if (cookieCredentials.isValid()) {
                log.info("Using cookie credentials for attendance upload");
                credentials = cookieCredentials;
            }
        }

        // Priority 3: Config values (fallback)
        if (credentials == null || !credentials.isValid()) {
            log.info("No valid cookie/request credentials, using config values");
            credentials = new ArtemisCredentials(
                    artemisConfig.getBaseUrl(),
                    artemisConfig.getJwtToken(),
                    artemisConfig.getUsername(),
                    artemisConfig.getPassword()
            );

            if (!credentials.isValid()) {
                log.warn("No valid credentials found. Authentication required.");
                return ResponseEntity.status(401).build();
            }
        }

        TeamsScheduleDTO results = attendanceService.parseAttendance(file, credentials, courseId, exerciseId);
        log.info("AttendanceResource: {}", results);
        return ResponseEntity.ok(results);
    }
}

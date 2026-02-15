package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.dto.TeamsScheduleDTO;
import de.tum.cit.aet.dataProcessing.service.AttendanceService;
import de.tum.cit.aet.dataProcessing.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * Constructs the AttendanceResource with required dependencies.
     *
     * @param attendanceService the attendance service
     * @param cryptoService the crypto service
     * @param artemisConfig the Artemis configuration
     */
    public AttendanceResource(
            AttendanceService attendanceService,
            CryptoService cryptoService,
            ArtemisConfig artemisConfig
    ) {
        this.attendanceService = attendanceService;
        this.cryptoService = cryptoService;
        this.artemisConfig = artemisConfig;
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
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        if (!credentials.isValid()) {
            log.info("No credentials in cookies, using config values");
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

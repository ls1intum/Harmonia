package de.tum.cit.aet.usermanagement.service;

import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.usermanagement.dto.LoginRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/**
 * Service handling the authentication business logic.
 * Delegates to Artemis for credential verification and instructor authorization.
 */
@Slf4j
@Service
public class AuthService {

    private final ArtemisClientService artemisClientService;
    private final CryptoService cryptoService;

    public AuthService(ArtemisClientService artemisClientService, CryptoService cryptoService) {
        this.artemisClientService = artemisClientService;
        this.cryptoService = cryptoService;
    }

    /**
     * Authenticates a user against Artemis, verifies instructor membership for the
     * given course, and returns the session cookies to be set on the HTTP response.
     *
     * @param loginRequest  the login credentials and course context
     * @param secureCookies whether cookies should carry the {@code Secure} flag
     * @return list of {@link ResponseCookie}s representing the authenticated session
     * @throws ResponseStatusException with {@code 403 Forbidden} if the user is not an instructor
     * @throws ResponseStatusException with {@code 400 Bad Request} if the courseId is not a valid number
     */
    public List<ResponseCookie> login(LoginRequestDTO loginRequest, boolean secureCookies) {
        // 1) Authenticate with Artemis and obtain JWT
        String jwtToken = artemisClientService.authenticate(
                loginRequest.serverUrl(),
                loginRequest.username(),
                loginRequest.password()
        );

        // 2) Verify instructor membership for the given course
        verifyInstructorRole(loginRequest, jwtToken);

        log.info("User '{}' successfully authenticated against Artemis", loginRequest.username());

        // 3) Encrypt the password and build session cookies
        String encryptedPassword = cryptoService.encrypt(loginRequest.password());

        return List.of(
                buildCookie("jwt", jwtToken, secureCookies),
                buildCookie("artemis_server_url", loginRequest.serverUrl(), secureCookies),
                buildCookie("artemis_username", loginRequest.username(), secureCookies),
                buildCookie("artemis_password", encryptedPassword, secureCookies)
        );
    }

    /**
     * Verifies that the authenticated user holds an instructor role for the requested course.
     *
     * @param loginRequest the login request containing username and courseId
     * @param jwtToken     the JWT obtained from Artemis authentication
     */
    private void verifyInstructorRole(LoginRequestDTO loginRequest, String jwtToken) {
        if (loginRequest.courseId() == null || loginRequest.courseId().isBlank()) {
            return;
        }

        try {
            long courseId = Long.parseLong(loginRequest.courseId());
            boolean isInstructor = artemisClientService.isUserInstructor(
                    loginRequest.serverUrl(), jwtToken, courseId, loginRequest.username());

            if (!isInstructor) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not an instructor for this course");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId format");
        }
    }

    private ResponseCookie buildCookie(String name, String value, boolean secure) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();
    }
}

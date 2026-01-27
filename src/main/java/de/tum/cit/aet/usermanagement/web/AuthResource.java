package de.tum.cit.aet.usermanagement.web;

import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.usermanagement.dto.LoginRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final ArtemisClientService artemisClientService;
    private final CryptoService cryptoService;
    private final boolean isSecureCookies;

    public AuthResource(ArtemisClientService artemisClientService,
                       CryptoService cryptoService,
                       @Value("${spring.profiles.active:prod}") String activeProfile) {
        this.artemisClientService = artemisClientService;
        this.cryptoService = cryptoService;
        // Only use secure cookies in production (not in local development)
        this.isSecureCookies = !"local".equals(activeProfile);
    }

    /**
     * Authenticates the user against Artemis and sets the necessary cookies.
     * Also verifies that the provided username is an instructor for the specified courseId (if provided).
     *
     * @param loginRequest The login request containing username, password, server URL and optional courseId
     * @return ResponseEntity with cookies set on success, or 403 if instructor verification fails
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        log.info("POST request received: /api/auth/login for server {}", loginRequest.serverUrl());

        // 1) Authenticate with Artemis and obtain JWT
        String jwtToken = artemisClientService.authenticate(
                loginRequest.serverUrl(),
                loginRequest.username(),
                loginRequest.password()
        );

        // 2) Verify instructor membership
        if (loginRequest.courseId() != null && !loginRequest.courseId().isBlank()) {
            try {
                Long courseId = Long.parseLong(loginRequest.courseId());
                boolean isInstructor = artemisClientService.isUserInstructor(loginRequest.serverUrl(), jwtToken, courseId, loginRequest.username());
                if (!isInstructor) {
                    log.info("User {} is not an instructor for course {}", loginRequest.username(), courseId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid courseId provided: {}", loginRequest.courseId());
                return ResponseEntity.badRequest().build();
            }
        }

        ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwtToken)
                .httpOnly(true)
                .secure(isSecureCookies)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        ResponseCookie serverUrlCookie = ResponseCookie.from("artemis_server_url", loginRequest.serverUrl())
                .httpOnly(true)
                .secure(isSecureCookies)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        ResponseCookie usernameCookie = ResponseCookie.from("artemis_username", loginRequest.username())
                .httpOnly(true)
                .secure(isSecureCookies)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        // Encrypt password before storing in cookie
        String encryptedPassword = cryptoService.encrypt(loginRequest.password());
        ResponseCookie passwordCookie = ResponseCookie.from("artemis_password", encryptedPassword)
                .httpOnly(true)
                .secure(isSecureCookies)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, serverUrlCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, usernameCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, passwordCookie.toString());

        return ResponseEntity.ok()
                .headers(headers)
                .build();
    }
}

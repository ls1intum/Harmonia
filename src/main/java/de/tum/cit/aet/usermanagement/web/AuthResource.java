package de.tum.cit.aet.usermanagement.web;

import de.tum.cit.aet.usermanagement.dto.LoginRequestDTO;
import de.tum.cit.aet.usermanagement.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing authentication endpoints.
 * Delegates all business logic to {@link AuthService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AuthService authService;
    private final boolean isSecureCookies;

    public AuthResource(AuthService authService,
                        @Value("${spring.profiles.active:prod}") String activeProfile) {
        this.authService = authService;
        this.isSecureCookies = !"local".equals(activeProfile);
    }

    /**
     * Authenticates the user against Artemis and sets the session cookies.
     *
     * @param loginRequest the login credentials and course context
     * @return {@code 200 OK} with session cookies on success
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        log.info("POST /api/auth/login for server {}", loginRequest.serverUrl());

        // 1) Delegate authentication and cookie creation to the service
        List<ResponseCookie> cookies = authService.login(loginRequest, isSecureCookies);

        // 2) Attach cookies to the response
        HttpHeaders headers = new HttpHeaders();
        cookies.forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));

        return ResponseEntity.ok().headers(headers).build();
    }
}

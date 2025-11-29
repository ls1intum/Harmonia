package de.tum.cit.aet.usermanagement.web;

import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.usermanagement.dto.LoginRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

    public AuthResource(ArtemisClientService artemisClientService, CryptoService cryptoService) {
        this.artemisClientService = artemisClientService;
        this.cryptoService = cryptoService;
    }

    /**
     * Authenticates the user against Artemis and sets the necessary cookies.
     *
     * @param loginRequest The login request containing username, password, and server URL
     * @return ResponseEntity with the cookies set
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        log.info("POST request received: /api/auth/login for server {}", loginRequest.serverUrl());
        String jwtToken = artemisClientService.authenticate(
                loginRequest.serverUrl(),
                loginRequest.username(),
                loginRequest.password()
        );

        ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwtToken)
                .httpOnly(true)
                .secure(true) // Set to true for production
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        ResponseCookie serverUrlCookie = ResponseCookie.from("artemis_server_url", loginRequest.serverUrl())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        ResponseCookie usernameCookie = ResponseCookie.from("artemis_username", loginRequest.username())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        // Encrypt password before storing in cookie
        String encryptedPassword = cryptoService.encrypt(loginRequest.password());
        ResponseCookie passwordCookie = ResponseCookie.from("artemis_password", encryptedPassword)
                .httpOnly(true)
                .secure(true)
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

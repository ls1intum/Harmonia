package de.tum.cit.aet.usermanagement.web;

import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.usermanagement.dto.LoginRequestDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ArtemisClientService artemisClientService;
    private final CryptoService cryptoService;

    public AuthController(ArtemisClientService artemisClientService, CryptoService cryptoService) {
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
    public ResponseEntity<Void> login(@RequestBody LoginRequestDTO loginRequest) {
        if (loginRequest.serverUrl() == null || loginRequest.serverUrl().isBlank() ||
            loginRequest.username() == null || loginRequest.username().isBlank() ||
            loginRequest.password() == null || loginRequest.password().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

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

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, serverUrlCookie.toString())
                .header(HttpHeaders.SET_COOKIE, usernameCookie.toString())
                .header(HttpHeaders.SET_COOKIE, passwordCookie.toString())
                .build();
    }
}

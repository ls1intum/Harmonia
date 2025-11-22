package de.tum.cit.aet.usermanagement.web;

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

    public AuthController(ArtemisClientService artemisClientService) {
        this.artemisClientService = artemisClientService;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequestDTO loginRequest) {
        String jwtToken = artemisClientService.authenticate(
                loginRequest.serverUrl(),
                loginRequest.username(),
                loginRequest.password()
        );

        ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwtToken)
                .httpOnly(true)
                .secure(false) // Set to false for localhost development, true for production usually
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .build();
    }
}

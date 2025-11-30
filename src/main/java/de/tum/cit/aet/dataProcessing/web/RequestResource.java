package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class RequestResource {

    private final RequestService requestService;
    private final CryptoService cryptoService;

    @Autowired
    public RequestResource(RequestService requestService, CryptoService cryptoService) {
        this.requestService = requestService;
        this.cryptoService = cryptoService;
    }

    /**
     * GET endpoint to fetch and clone all repositories.
     * Triggers the fetching of participations from Artemis and clones/pulls all repositories.
     *
     * @param jwtToken The JWT token from the cookie
     * @param serverUrl The Artemis server URL from the cookie
     * @param username The Artemis username from the cookie
     * @param encryptedPassword The encrypted Artemis password from the cookie
     * @return ResponseEntity containing the list of TeamRepositoryDTO
     */
    @GetMapping("fetchAndCloneRepositories")
    public ResponseEntity<List<TeamRepositoryDTO>> fetchAndCloneRepositories(
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword
    ) {
        log.info("GET request received: fetchAndCloneRepositories");

        String password = decryptPassword(encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        if (!credentials.isValid()) {
            log.warn("No credentials found in cookies. Authentication required.");
            return ResponseEntity.status(401).build();
        }

        List<TeamRepositoryDTO> repositories = requestService.fetchAndCloneRepositories(credentials);
        log.info("Successfully fetched and cloned {} repositories", repositories.size());
        return ResponseEntity.ok(repositories);
    }

    private String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null) {
            return null;
        }
        try {
            return cryptoService.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.error("Failed to decrypt password from cookie", e);
            return null;
        }
    }
}

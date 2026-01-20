package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.dataProcessing.util.CredentialUtils;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class RequestResource {

    private final RequestService requestService;
    private final CryptoService cryptoService;
    private final ArtemisConfig artemisConfig;

    @Autowired
    public RequestResource(RequestService requestService, CryptoService cryptoService, ArtemisConfig artemisConfig) {
        this.requestService = requestService;
        this.cryptoService = cryptoService;
        this.artemisConfig = artemisConfig;
    }

    /**
     * GET endpoint to fetch, analyze, and save repository data.
     * Triggers the fetching of participations from Artemis, analyzes repositories, and saves the data.
     *
     * @param exerciseId        The exercise ID to fetch participations for
     * @param jwtToken          The JWT token from the cookie
     * @param serverUrl         The Artemis server URL from the cookie
     * @param username          The Artemis username from the cookie
     * @param encryptedPassword The encrypted Artemis password from the cookie
     * @return ResponseEntity containing the list of ClientResponseDTO
     */
    @GetMapping("fetchData")
    public ResponseEntity<List<ClientResponseDTO>> fetchData(
            @RequestParam(value = "exerciseId") Long exerciseId,
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @CookieValue(value = "artemis_server_url", required = false) String serverUrl,
            @CookieValue(value = "artemis_username", required = false) String username,
            @CookieValue(value = "artemis_password", required = false) String encryptedPassword
    ) {
        log.info("GET request received: fetchData for exerciseId: {}", exerciseId);

        String password = CredentialUtils.decryptPassword(cryptoService, encryptedPassword);
        ArtemisCredentials credentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);

        // Fallback to config if cookies are missing
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

        requestService.fetchAnalyzeAndSaveRepositories(credentials, exerciseId);
        List<ClientResponseDTO> clientResponseDTOS = requestService.getAllRepositoryData();
        return ResponseEntity.ok(clientResponseDTOS);
    }

    /**
     * GET endpoint to retrieve already-analyzed data from database without re-analyzing.
     *
     * @return ResponseEntity containing the list of ClientResponseDTO
     */
    @GetMapping("getData")
    public ResponseEntity<List<ClientResponseDTO>> getData() {
        log.info("GET request received: getData (from database)");
        List<ClientResponseDTO> clientResponseDTOS = requestService.getAllRepositoryData();
        return ResponseEntity.ok(clientResponseDTOS);
    }
}

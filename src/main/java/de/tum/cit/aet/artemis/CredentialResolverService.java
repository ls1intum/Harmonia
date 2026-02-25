package de.tum.cit.aet.artemis;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.security.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves Artemis credentials from multiple sources with a fixed priority:
 * 1) Explicit request parameters (freshly entered by user),
 * 2) Cookie values (may be stale),
 * 3) Application config fallback.
 */
@Slf4j
@Service
public class CredentialResolverService {

    private final ArtemisClientService artemisClientService;
    private final CryptoService cryptoService;
    private final ArtemisConfig artemisConfig;

    public CredentialResolverService(ArtemisClientService artemisClientService,
                                     CryptoService cryptoService,
                                     ArtemisConfig artemisConfig) {
        this.artemisClientService = artemisClientService;
        this.cryptoService = cryptoService;
        this.artemisConfig = artemisConfig;
    }

    /**
     * Resolves credentials from request parameters, cookies, or config fallback.
     *
     * @param jwtToken          JWT from cookie (may be {@code null})
     * @param serverUrl         server URL from cookie (may be {@code null})
     * @param username          username from cookie (may be {@code null})
     * @param encryptedPassword encrypted password from cookie (may be {@code null})
     * @param requestServerUrl  server URL from request param (may be {@code null})
     * @param requestUsername   username from request param (may be {@code null})
     * @param requestPassword   password from request param (may be {@code null})
     * @return resolved {@link ArtemisCredentials}, never {@code null}
     */
    public ArtemisCredentials resolve(String jwtToken, String serverUrl,
                                      String username, String encryptedPassword,
                                      String requestServerUrl, String requestUsername,
                                      String requestPassword) {
        // 1) Request credentials (freshest)
        if (StringUtils.hasText(requestServerUrl) && StringUtils.hasText(requestUsername)
                && StringUtils.hasText(requestPassword)) {
            try {
                String jwt = artemisClientService.authenticate(requestServerUrl, requestUsername, requestPassword);
                return new ArtemisCredentials(requestServerUrl, jwt, requestUsername, requestPassword);
            } catch (Exception e) {
                log.warn("Request credential authentication failed: {}", e.getMessage());
            }
        }

        // 2) Cookie credentials
        String password = decryptPassword(encryptedPassword);
        ArtemisCredentials cookieCredentials = new ArtemisCredentials(serverUrl, jwtToken, username, password);
        if (cookieCredentials.isValid()) {
            return cookieCredentials;
        }

        // 3) Config fallback
        return new ArtemisCredentials(
                artemisConfig.getBaseUrl(), artemisConfig.getJwtToken(),
                artemisConfig.getUsername(), artemisConfig.getPassword());
    }

    /**
     * Resolves credentials from cookie values or config fallback (no request params).
     *
     * @param jwtToken          JWT from cookie (may be {@code null})
     * @param serverUrl         server URL from cookie (may be {@code null})
     * @param username          username from cookie (may be {@code null})
     * @param encryptedPassword encrypted password from cookie (may be {@code null})
     * @return resolved {@link ArtemisCredentials}, never {@code null}
     */
    public ArtemisCredentials resolve(String jwtToken, String serverUrl,
                                      String username, String encryptedPassword) {
        return resolve(jwtToken, serverUrl, username, encryptedPassword, null, null, null);
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

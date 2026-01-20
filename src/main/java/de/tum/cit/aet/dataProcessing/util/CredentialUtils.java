package de.tum.cit.aet.dataProcessing.util;

import de.tum.cit.aet.core.security.CryptoService;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods for handling credentials and authentication.
 */
@Slf4j
public final class CredentialUtils {

    private CredentialUtils() {
        // Utility class
    }

    /**
     * Decrypts an encrypted password using the provided CryptoService.
     *
     * @param cryptoService     the crypto service to use for decryption
     * @param encryptedPassword the encrypted password
     * @return the decrypted password, or null if decryption fails or input is null
     */
    public static String decryptPassword(CryptoService cryptoService, String encryptedPassword) {
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

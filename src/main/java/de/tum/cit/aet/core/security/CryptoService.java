package de.tum.cit.aet.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service providing AES-GCM encryption and decryption.
 * Uses a 128-bit key derived from a configurable secret and a random 12-byte IV per operation.
 */
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKeySpec secretKey;

    public CryptoService(@Value("${harmonia.security.secret-key:default-secret-key-change-me-in-prod}") String secret) {
        this.secretKey = deriveKey(secret);
    }

    /**
     * Encrypts a plaintext string using AES-GCM.
     * The returned value is Base64-encoded and contains the IV prepended to the ciphertext.
     *
     * @param plaintext the string to encrypt
     * @return the Base64-encoded ciphertext (IV + encrypted data)
     */
    public String encrypt(String plaintext) {
        try {
            // 1) Generate a random initialization vector
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // 2) Encrypt the plaintext
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 3) Prepend IV to ciphertext and encode as Base64
            byte[] encrypted = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting", e);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext that was produced by {@link #encrypt(String)}.
     *
     * @param ciphertext the Base64-encoded string to decrypt (IV + encrypted data)
     * @return the original plaintext
     */
    public String decrypt(String ciphertext) {
        try {
            // 1) Decode and extract the IV from the first 12 bytes
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            // 2) Decrypt the remaining bytes
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return new String(cipher.doFinal(decoded, GCM_IV_LENGTH, decoded.length - GCM_IV_LENGTH));
        } catch (Exception e) {
            throw new RuntimeException("Error while decrypting", e);
        }
    }

    /**
     * Derives a 128-bit AES key from the given secret using SHA-256.
     */
    private SecretKeySpec deriveKey(String secret) {
        try {
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Error generating security key", e);
        }
    }
}

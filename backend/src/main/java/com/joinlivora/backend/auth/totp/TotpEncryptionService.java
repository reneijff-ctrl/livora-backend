package com.joinlivora.backend.auth.totp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for TOTP secrets at rest.
 * Key is sourced from the TOTP_ENCRYPTION_KEY environment variable (32-byte Base64-encoded value).
 */
@Service
public class TotpEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public TotpEncryptionService(@Value("${livora.totp.encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "Invalid AES key length: " + keyBytes.length + " bytes. " +
                "TOTP_ENCRYPTION_KEY must be a Base64-encoded value of exactly 16, 24, or 32 bytes."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a plaintext TOTP secret. Returns Base64-encoded IV + ciphertext.
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt TOTP secret", e);
        }
    }

    /**
     * Decrypts a Base64-encoded IV + ciphertext back to the plaintext TOTP secret.
     */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt TOTP secret", e);
        }
    }
}

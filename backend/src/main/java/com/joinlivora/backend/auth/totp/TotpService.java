package com.joinlivora.backend.auth.totp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TotpService {

    private static final int BACKUP_CODE_COUNT = 5;
    private static final String BACKUP_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BACKUP_CODE_LENGTH = 8;

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final TotpEncryptionService encryptionService;
    private final TotpBackupCodeRepository backupCodeRepository;

    public TotpService(TotpEncryptionService encryptionService,
                       TotpBackupCodeRepository backupCodeRepository) {
        this.encryptionService = encryptionService;
        this.backupCodeRepository = backupCodeRepository;
    }

    public TotpBackupCodeRepository getBackupCodeRepository() {
        return backupCodeRepository;
    }

    /**
     * Generates a new TOTP secret and returns it encrypted for storage.
     */
    public String generateSecret() {
        GoogleAuthenticatorKey credentials = gAuth.createCredentials();
        return encryptionService.encrypt(credentials.getKey());
    }

    /**
     * Returns the raw (plaintext) secret for QR code generation.
     * The stored secret must be decrypted first.
     */
    public String decryptSecret(String encryptedSecret) {
        return encryptionService.decrypt(encryptedSecret);
    }

    /**
     * Verifies a TOTP code against an encrypted secret.
     */
    public boolean verifyCode(String encryptedSecret, int code) {
        String plainSecret = encryptionService.decrypt(encryptedSecret);
        return gAuth.authorize(plainSecret, code);
    }

    /**
     * Builds the otpauth:// QR URL using the plaintext secret.
     */
    public String getQRUrl(String email, String encryptedSecret) {
        String plainSecret = encryptionService.decrypt(encryptedSecret);
        return String.format(
                "otpauth://totp/JoinLivora:%s?secret=%s&issuer=JoinLivora",
                email,
                plainSecret
        );
    }

    /**
     * Generates 5 single-use backup codes, stores them hashed, and returns the plaintext codes.
     * Any existing unused codes for the user are replaced.
     */
    @Transactional
    public List<String> generateBackupCodes(Long userId) {
        backupCodeRepository.deleteAllByUserId(userId);

        SecureRandom random = new SecureRandom();
        List<String> plainCodes = new ArrayList<>(BACKUP_CODE_COUNT);

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                sb.append(BACKUP_CODE_CHARS.charAt(random.nextInt(BACKUP_CODE_CHARS.length())));
            }
            String plain = sb.toString();
            plainCodes.add(plain);

            TotpBackupCode entity = TotpBackupCode.builder()
                    .userId(userId)
                    .codeHash(bcrypt.encode(plain))
                    .used(false)
                    .build();
            backupCodeRepository.save(entity);
        }

        return plainCodes;
    }

    /**
     * Attempts to consume a backup code. Returns true and marks it used if valid.
     */
    @Transactional
    public boolean verifyAndConsumeBackupCode(Long userId, String inputCode) {
        List<TotpBackupCode> codes = backupCodeRepository.findAllByUserIdAndUsedFalse(userId);
        for (TotpBackupCode code : codes) {
            if (bcrypt.matches(inputCode.toUpperCase(), code.getCodeHash())) {
                code.setUsed(true);
                code.setUsedAt(Instant.now());
                backupCodeRepository.save(code);
                return true;
            }
        }
        return false;
    }
}

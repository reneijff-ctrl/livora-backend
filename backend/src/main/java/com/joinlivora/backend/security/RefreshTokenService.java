package com.joinlivora.backend.security;

import com.joinlivora.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${security.jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // =========================
    // SHA-256 HASHING
    // =========================

    /**
     * Computes SHA-256 hash of the plain token value.
     * Returns a 64-character lowercase hex string.
     */
    public static String sha256(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // =========================
    // CREATE REFRESH TOKEN
    // =========================

    public RefreshToken create(User user) {
        String plainToken = UUID.randomUUID().toString();
        String hash = sha256(plainToken);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash);
        token.setExpiryDate(Instant.now().plusSeconds(refreshTokenExpiration));
        token.setRevoked(false);

        RefreshToken savedToken = refreshTokenRepository.save(token);
        // Carry the plain token value back to the caller via transient field
        savedToken.setPlainToken(plainToken);
        return savedToken;
    }

    // =========================
    // VERIFY + GET (O(1) lookup)
    // =========================

    @Transactional(readOnly = true)
    public RefreshToken verifyAndGet(String plainTokenValue) {
        String hash = sha256(plainTokenValue);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    logger.warn("SECURITY: Invalid refresh token attempt detected");
                    return new TokenExpiredException("Invalid refresh token");
                });

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            logger.info("SECURITY: Expired refresh token used and deleted for user: {}", token.getUser().getEmail());
            throw new TokenExpiredException("Refresh token expired");
        }

        return token;
    }

    // =========================
    // ROTATE (O(1) lookup)
    // =========================

    @Transactional
    public RefreshToken rotateRefreshToken(String oldPlainTokenValue) {
        String oldHash = sha256(oldPlainTokenValue);

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> new TokenExpiredException("Unauthorized: Refresh token is invalid"));

        // Reuse detection: if this token was already revoked, someone is reusing a stolen token
        if (oldToken.isRevoked()) {
            logger.warn("SECURITY CRITICAL: Refresh token reuse detected for user: {}. Token ID: {}",
                    oldToken.getUser().getEmail(), oldToken.getId());
            // Revoke ALL tokens for this user to stop potential attack
            refreshTokenRepository.revokeAllByUser(oldToken.getUser());
            throw new TokenExpiredException("Unauthorized: Refresh token is invalid or compromised");
        }

        if (oldToken.getExpiryDate().isBefore(Instant.now())) {
            throw new TokenExpiredException("Unauthorized: Refresh token is expired");
        }

        // Create new token
        RefreshToken newToken = create(oldToken.getUser());

        // Revoke old token
        oldToken.setRevoked(true);
        oldToken.setReplacedByToken(newToken.getTokenHash());
        refreshTokenRepository.save(oldToken);

        return newToken;
    }

    public RefreshToken rotate(RefreshToken oldToken) {
        // oldToken.getPlainToken() may be set if this was just created,
        // otherwise we need the plain value from the caller
        String plainValue = oldToken.getPlainToken();
        if (plainValue == null) {
            throw new IllegalStateException("Cannot rotate token without plain value");
        }
        return rotateRefreshToken(plainValue);
    }

    // =========================
    // REVOKE (O(1) lookup)
    // =========================

    @Transactional
    public void revokeToken(String plainTokenValue) {
        String hash = sha256(plainTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            logger.info("SECURITY: Refresh token revoked for user: {}", token.getUser().getEmail());
        });
    }

    @Transactional(readOnly = true)
    public String getEmailFromToken(String plainTokenValue) {
        String hash = sha256(plainTokenValue);
        return refreshTokenRepository.findByTokenHash(hash)
                .map(t -> t.getUser().getEmail())
                .orElse(null);
    }

    // =========================
    // DELETE (LOGOUT)
    // =========================

    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }

    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    // =========================
    // SCHEDULED CLEANUP
    // =========================

    /**
     * Runs every 6 hours. Deletes expired tokens and revoked tokens older than 7 days.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();

        int expiredCount = refreshTokenRepository.deleteExpiredTokens(now);
        if (expiredCount > 0) {
            logger.info("CLEANUP: Deleted {} expired refresh tokens", expiredCount);
        }

        Instant revokedCutoff = now.minusSeconds(7 * 24 * 60 * 60);
        int revokedCount = refreshTokenRepository.deleteRevokedTokensBefore(revokedCutoff);
        if (revokedCount > 0) {
            logger.info("CLEANUP: Deleted {} revoked refresh tokens older than 7 days", revokedCount);
        }
    }
}

package com.joinlivora.backend.security;

import com.joinlivora.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;
    private final PasswordEncoder passwordEncoder;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${security.jwt.refresh-token-expiration}") long refreshTokenExpiration,
            PasswordEncoder passwordEncoder
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.passwordEncoder = passwordEncoder;
    }

    // =========================
    // AANMAKEN REFRESH TOKEN
    // =========================
    public RefreshToken create(User user) {
        String plainToken = UUID.randomUUID().toString();
        
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        // Store hashed token in DB
        token.setToken(passwordEncoder.encode(plainToken));
        token.setExpiryDate(
                Instant.now().plusSeconds(refreshTokenExpiration)
        );

        RefreshToken savedToken = refreshTokenRepository.save(token);
        // Temporarily set the plain token so it can be sent to the client
        // Note: This won't be saved to DB as the plain value
        savedToken.setToken(plainToken);
        return savedToken;
    }

    // =========================
    // VALIDEREN + OPHALEN
    // =========================
    public RefreshToken verifyAndGet(String tokenValue) {
        // Since tokens are hashed, we find all active tokens and match.
        // In a production app, we would use a public ID to look up the token.
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByRevokedFalse();
        
        RefreshToken token = activeTokens.stream()
                .filter(t -> passwordEncoder.matches(tokenValue, t.getToken()))
                .findFirst()
                .orElseGet(() -> {
                    logger.warn("SECURITY: Invalid refresh token attempt detected");
                    throw new TokenExpiredException("Invalid refresh token");
                });

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            logger.info("SECURITY: Expired refresh token used and deleted for creator: {}", token.getUser().getEmail());
            throw new TokenExpiredException("Refresh token expired");
        }

        return token;
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenValue) {
        // Find all tokens (including revoked ones to detect reuse)
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        
        RefreshToken oldToken = allTokens.stream()
                .filter(t -> passwordEncoder.matches(oldTokenValue, t.getToken()))
                .findFirst()
                .orElseThrow(() -> new TokenExpiredException("Unauthorized: Refresh token is invalid"));

        // Reuse Detection Logic
        if (oldToken.isRevoked()) {
            logger.warn("SECURITY CRITICAL: Refresh token reuse detected for creator: {}. Token ID: {}", oldToken.getUser().getEmail(), oldToken.getId());
            // Invalidate ALL tokens for the creator to stop the potential attack
            refreshTokenRepository.revokeAllByUser(oldToken.getUser());
            throw new TokenExpiredException("Unauthorized: Refresh token is invalid or compromised");
        }

        if (oldToken.getExpiryDate().isBefore(Instant.now())) {
            throw new TokenExpiredException("Unauthorized: Refresh token is expired");
        }

        // Rotation: Issue NEW token and revoke OLD one
        RefreshToken newToken = create(oldToken.getUser());

        oldToken.setRevoked(true);
        // We store the hashed version of the NEW token as a referenceId if needed
        // but since newToken.getToken() currently returns the plain value (see create)
        // we should hash it or just store a marker.
        oldToken.setReplacedByToken("ROTATED"); 
        refreshTokenRepository.save(oldToken);

        return newToken;
    }

    public RefreshToken rotate(RefreshToken oldToken) {
        return rotateRefreshToken(oldToken.getToken());
    }

    // =========================
    // VERWIJDEREN (LOGOUT)
    // =========================
    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }

    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public String getEmailFromToken(String tokenValue) {
        // Since tokens are hashed, we need to find it by scanning active tokens
        // This is not efficient, but matches existing logic in rotateRefreshToken
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByRevokedFalse();
        return activeTokens.stream()
                .filter(t -> passwordEncoder.matches(tokenValue, t.getToken()))
                .findFirst()
                .map(t -> t.getUser().getEmail())
                .orElse(null);
    }
}

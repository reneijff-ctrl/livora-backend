package com.joinlivora.backend.streaming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stateless HMAC-SHA256 token service for HLS segment access with IP binding.
 *
 * <h3>Token format (v2 — IP-bound)</h3>
 * <pre>{base64url(streamId ":" expiryEpochSeconds ":" ipHash)}_{hex(hmac)}</pre>
 * The {@code ipHash} is the first 8 hex characters of SHA-256(clientIp + secret).
 * This binds the token to the requesting IP without exposing the raw IP in the token.
 *
 * <h3>Anti-leech protection</h3>
 * <p>Without IP binding, a viewer who obtains an HLS URL can share it with others
 * (hotlinking / stream restreaming). The shared URL remains valid until expiry.
 * With IP binding, the token is only valid from the original client's IP address.
 * Even if the URL is shared, requests from different IPs are rejected at the Nginx
 * {@code auth_request} layer — zero origin cost for rejected requests.
 *
 * <h3>TTL strategy</h3>
 * <ul>
 *   <li>Default TTL: 2–5 minutes (short enough to prevent sharing, long enough for
 *       playlist polling cycles)</li>
 *   <li>Token refresh: the player should call {@code /api/hls/token} every 3 minutes</li>
 * </ul>
 *
 * <h3>Usage flow</h3>
 * <ol>
 *   <li>Client authenticates once via WebSocket / stream-join API.</li>
 *   <li>Backend calls {@link #generateToken(UUID, String)} with streamId + clientIp.</li>
 *   <li>Client embeds the token in all HLS URLs as {@code ?t={token}}.</li>
 *   <li>Nginx {@code auth_request} forwards to
 *       {@code /api/hls/validate?streamId=…&t=…&ip=…} — no DB call is made.</li>
 * </ol>
 *
 * <p>Zero database calls in the validation path. All data (streamId, expiry, ipHash) is
 * embedded in the token and authenticated by the HMAC signature.
 *
 * <h3>Backward compatibility</h3>
 * <p>Tokens without an IP segment (legacy v1 format) are still accepted when
 * {@code hls.token.ip-binding-enabled=false}. Default is false to allow gradual rollout.
 */
@Service
@Slf4j
public class HlsTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Token validity window in seconds (default 3 minutes for IP-bound tokens). */
    private final long tokenTtlSeconds;

    private final byte[] secretKey;

    /** Whether to enforce IP binding on token validation. Default false for gradual rollout. */
    private final boolean ipBindingEnabled;

    public HlsTokenService(
            @Value("${hls.token.secret:${JWT_SECRET}}") String secret,
            @Value("${hls.token.ttl-seconds:180}") long tokenTtlSeconds,
            @Value("${hls.token.ip-binding-enabled:false}") boolean ipBindingEnabled
    ) {
        // Derive a 32-byte key from whatever secret is configured
        this.secretKey = deriveKey(secret);
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.ipBindingEnabled = ipBindingEnabled;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Generates a short-lived HMAC token for the given stream, without IP binding.
     * Use {@link #generateToken(UUID, String)} for IP-bound (anti-leech) tokens.
     *
     * @param streamId UUID of the stream
     * @return URL-safe token string
     */
    public String generateToken(UUID streamId) {
        return generateToken(streamId, null);
    }

    /**
     * Generates a short-lived HMAC token bound to the given client IP address.
     *
     * <p>The IP is not stored verbatim — only the first 8 hex chars of HMAC(ip) are
     * embedded, making it infeasible to reverse-engineer the original IP from the token.
     *
     * @param streamId UUID of the stream
     * @param clientIp the client's IP address (IPv4 or IPv6); null = no IP binding
     * @return URL-safe token string
     */
    public String generateToken(UUID streamId, String clientIp) {
        long expiry = Instant.now().getEpochSecond() + tokenTtlSeconds;
        String ipSegment = (ipBindingEnabled && clientIp != null)
                ? ":" + hmacHex(clientIp).substring(0, 8)
                : "";
        String payload = streamId.toString() + ":" + expiry + ipSegment;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = hmacHex(payloadB64);
        return payloadB64 + "_" + signature;
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Validates an HLS token without IP check.
     * Delegates to {@link #validateToken(UUID, String, String)} with no IP.
     *
     * @param streamId  the stream UUID claimed in the request URL
     * @param token     the token string passed as {@code ?t=…}
     * @return {@code true} if the token is valid, not expired, and matches the stream
     */
    public boolean validateToken(UUID streamId, String token) {
        return validateToken(streamId, token, null);
    }

    /**
     * Validates an HLS token with optional IP binding check.
     *
     * <p><strong>No database calls are made.</strong> All required information
     * is embedded in the token itself.
     *
     * @param streamId  the stream UUID claimed in the request URL
     * @param token     the token string passed as {@code ?t=…}
     * @param clientIp  the requesting client's IP (from {@code CF-Connecting-IP} or
     *                  {@code X-Forwarded-For}); null skips IP check
     * @return {@code true} if the token is valid, not expired, matches the stream,
     *         and (if IP binding is enabled) matches the requesting IP
     */
    public boolean validateToken(UUID streamId, String token, String clientIp) {
        if (token == null || token.isBlank()) {
            return false;
        }

        int sep = token.lastIndexOf('_');
        if (sep < 1 || sep >= token.length() - 1) {
            log.debug("HLS_TOKEN_INVALID: malformed token for streamId={}", streamId);
            return false;
        }

        String payloadB64 = token.substring(0, sep);
        String providedSig = token.substring(sep + 1);

        // 1. Verify HMAC — constant-time comparison prevents timing attacks
        String expectedSig = hmacHex(payloadB64);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            log.debug("HLS_TOKEN_INVALID: signature mismatch for streamId={}", streamId);
            return false;
        }

        // 2. Decode payload and verify expiry + streamId + IP binding
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(payloadB64),
                    StandardCharsets.UTF_8);
            // Format: streamId:expiry[:ipHash8]
            String[] parts = payload.split(":", 3);
            if (parts.length < 2) {
                return false;
            }
            UUID tokenStreamId = UUID.fromString(parts[0]);
            long expiry = Long.parseLong(parts[1]);

            if (!tokenStreamId.equals(streamId)) {
                log.debug("HLS_TOKEN_INVALID: streamId mismatch — token={} request={}", tokenStreamId, streamId);
                return false;
            }

            if (Instant.now().getEpochSecond() > expiry) {
                log.debug("HLS_TOKEN_EXPIRED: streamId={} expiry={}", streamId, expiry);
                return false;
            }

            // 3. IP binding check (only if token has ipHash segment and IP binding is enabled)
            if (ipBindingEnabled && clientIp != null && parts.length == 3) {
                String tokenIpHash = parts[2];
                String requestIpHash = hmacHex(clientIp).substring(0, 8);
                if (!constantTimeEquals(tokenIpHash, requestIpHash)) {
                    log.warn("HLS_TOKEN_IP_MISMATCH: stream={} — possible hotlink/leech attempt",
                            streamId);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.debug("HLS_TOKEN_INVALID: decode error for streamId={}: {}", streamId, e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /**
     * Derives a 32-byte key from the application secret using the first 32 bytes
     * of its UTF-8 encoding, padded with zeros if shorter.
     */
    private static byte[] deriveKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, 32));
        return key;
    }

    /** Constant-time string equality to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}

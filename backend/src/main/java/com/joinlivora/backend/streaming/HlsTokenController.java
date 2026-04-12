package com.joinlivora.backend.streaming;

import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * HLS token endpoints — one for generating tokens (authenticated), one for
 * validating them (internal Nginx {@code auth_request} only, no DB).
 *
 * <h3>Flow</h3>
 * <pre>
 *  Client  ─────── GET /api/hls/token?streamId={uuid} ──────► Backend (JWT auth)
 *  Client  ◄────── 200 { "token": "…", "expiresIn": 300 } ───  Backend
 *
 *  Player  ─────── GET /hls/{streamId}/master.m3u8?t={token} ► Nginx
 *  Nginx   ─────── GET /api/hls/validate?streamId=…&t=… ──────► Backend (internal)
 *  Backend ◄─────────────────────────────────────────────────  (no DB call)
 *  Nginx   ◄─────── 200 (allow) or 403 (deny) ────────────────  Backend
 *  Player  ◄─────── file bytes ────────────────────────────────  Nginx
 * </pre>
 */
@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
@Slf4j
public class HlsTokenController {

    private final HlsTokenService tokenService;
    private final StreamRepository streamRepository;
    private final UserService userService;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;
    private final MetricsService metricsService;

    // ── Token generation (requires JWT authentication) ───────────────────────

    /**
     * Issues a short-lived HLS access token for the given stream, IP-bound when enabled.
     *
     * <p>This endpoint IS guarded by Spring Security ({@code authenticated()}).
     * It performs the full access-control check — stream must be live and the
     * caller must have view access.  The resulting token is then passed as
     * {@code ?t=…} on every HLS URL; Nginx validates it without touching the DB.
     *
     * <p>The client IP is extracted from the {@code CF-Connecting-IP} header (set by
     * Cloudflare) and embedded in the token when {@code hls.token.ip-binding-enabled=true}.
     *
     * @param streamId the stream UUID
     * @param principal the authenticated caller
     * @param request   the HTTP request (for client IP extraction)
     * @return JSON {@code { "token": "…", "expiresIn": 180 }}
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> generateToken(
            @RequestParam UUID streamId,
            Principal principal,
            HttpServletRequest request
    ) {
        // Re-use the same access logic as HlsProxyController to remain consistent
        Stream stream = streamRepository.findByIdWithCreator(streamId).orElse(null);
        if (stream == null) {
            log.warn("HLS_TOKEN_DENIED: stream not found streamId={}", streamId);
            return ResponseEntity.notFound().build();
        }

        try {
            if (!liveStreamService.isStreamActive(stream.getCreator().getId())) {
                log.debug("HLS_TOKEN_DENIED: stream not live streamId={}", streamId);
                return ResponseEntity.status(403).build();
            }
        } catch (Exception e) {
            log.warn("HLS_TOKEN_DENIED: isStreamActive error streamId={}: {}", streamId, e.getMessage());
            return ResponseEntity.status(403).build();
        }

        User user = null;
        if (principal != null) {
            user = userService.resolveUserFromSubject(principal.getName()).orElse(null);
        }

        if (!liveStreamService.validateViewerAccess(stream, user)) {
            log.debug("HLS_TOKEN_DENIED: viewer access denied streamId={} user={}",
                    streamId, principal != null ? principal.getName() : "anonymous");
            return ResponseEntity.status(403).build();
        }

        // Extract real client IP: CF-Connecting-IP (Cloudflare) > X-Forwarded-For > remote addr
        String clientIp = extractClientIp(request);
        String token = tokenService.generateToken(streamId, clientIp);
        log.debug("HLS_TOKEN_ISSUED: streamId={} user={} ip={}", streamId,
                principal != null ? principal.getName() : "anonymous", clientIp);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", 180
        ));
    }

    // ── Token validation (internal Nginx auth_request — NO DB CALLS) ─────────

    /**
     * Validates an HLS access token for Nginx {@code auth_request}.
     *
     * <p><strong>This endpoint must remain extremely fast: no DB calls, no Redis
     * calls, no blocking I/O.</strong>  It is called by Nginx on every segment
     * and playlist request — throughput at 10k viewers is ~2,500 req/s.
     *
     * <p>Spring Security must allow this path without authentication
     * ({@code .requestMatchers("/api/hls/validate").permitAll()}) because the
     * caller is Nginx (internal), not an end-user with a JWT cookie.
     *
     * <p>The client IP is forwarded by Nginx via the {@code X-Real-IP} header in the
     * {@code auth_request} sub-request when IP binding is enabled.
     *
     * @param streamId UUID of the stream from the Nginx request URI
     * @param token    the HLS token from the {@code ?t=…} query parameter
     * @param request  the HTTP request (for client IP extraction)
     * @return 200 if valid, 403 if invalid or expired
     */
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(
            @RequestParam UUID streamId,
            @RequestParam String token,
            HttpServletRequest request
    ) {
        // Count every validate call that reaches origin (CDN miss or direct access)
        metricsService.getHlsOriginRequests().increment();

        String clientIp = extractClientIp(request);
        if (tokenService.validateToken(streamId, token, clientIp)) {
            return ResponseEntity.ok().build();
        }
        log.debug("HLS_TOKEN_REJECTED: streamId={}", streamId);
        metricsService.getHlsTokenRejections().increment();
        return ResponseEntity.status(403).build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the real client IP from the request.
     * Priority: {@code CF-Connecting-IP} (Cloudflare) > {@code X-Real-IP} > remote addr.
     */
    private String extractClientIp(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

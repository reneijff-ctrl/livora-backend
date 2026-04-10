package com.joinlivora.backend.security;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.joinlivora.backend.config.MetricsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final AbuseDetectionService abuseDetectionService;
    private final MetricsService metricsService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCircuitBreaker;

    @Value("${livora.security.rate-limit.anonymous:30}")
    private int anonymousLimit;

    @Value("${livora.security.rate-limit.authenticated:100}")
    private int authenticatedLimit;

    @Value("${livora.security.rate-limit.creator:200}")
    private int creatorLimit;

    @Value("${livora.security.rate-limit.admin:5000}")
    private int adminLimit;

    @Value("${livora.security.rate-limit.websocket:1000}")
    private int websocketLimit;

    @Value("${livora.security.rate-limit.login:5}")
    private int loginLimit;

    @Value("${livora.security.rate-limit.register:3}")
    private int registerLimit;

    // Window duration in seconds — all limits share a 60-second sliding window
    private static final long WINDOW_SECONDS = 60L;

    public RateLimitingFilter(StringRedisTemplate redisTemplate,
                               AbuseDetectionService abuseDetectionService,
                               MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.abuseDetectionService = abuseDetectionService;
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest httpRequest,
            @NonNull HttpServletResponse httpResponse,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String path = httpRequest.getRequestURI();
        String clientIp = getClientIP(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() &&
                !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);

        String userKey = isAuthenticated ? "rl:user:" + authentication.getName() : "rl:ip:" + clientIp;

        // WebSocket connections get higher limits
        if (path.startsWith("/ws")) {
            if (!tryConsume(userKey + ":ws", websocketLimit)) {
                log.warn("RATE LIMIT EXCEEDED: {}:ws — WebSocket rate limit. IP: {}", userKey, maskIp(clientIp));
                sendError(httpResponse, 1, "websocket", userKey);
                return;
            }
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        // HLS content has its own rate limiting in HlsProxyController
        if (path.startsWith("/hls/")) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        if (path.startsWith("/auth/login") || path.startsWith("/api/auth/login")) {
            // Login: loginLimit attempts per minute per IP (strict)
            String key = "rl:endpoint:auth:login:" + clientIp;
            if (!tryConsume(key, loginLimit)) {
                log.warn("RATE LIMIT EXCEEDED: {} — Login rate limit. IP: {}, UA: {}", key, maskIp(clientIp), userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.LOGIN_BRUTE_FORCE, "Login rate limit exceeded");
                sendError(httpResponse, 60, "login", clientIp);
                return;
            }
        } else if (path.startsWith("/auth/register") || path.startsWith("/api/auth/register")) {
            // Register: registerLimit attempts per minute per IP
            String key = "rl:endpoint:auth:register:" + clientIp;
            if (!tryConsume(key, registerLimit)) {
                log.warn("RATE LIMIT EXCEEDED: {} — Register rate limit. IP: {}, UA: {}", key, maskIp(clientIp), userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.SUSPICIOUS_API_USAGE, "Register rate limit exceeded");
                sendError(httpResponse, 60, "register", clientIp);
                return;
            }
        } else if (path.startsWith("/webhooks/stripe") || path.startsWith("/api/payments/webhook") || path.startsWith("/api/stripe/webhook")) {
            // Stripe Webhooks: higher limit to avoid dropping events during spikes
            String key = "rl:endpoint:stripe-webhook";
            if (!tryConsume(key, 100)) {
                log.error("RATE LIMIT EXCEEDED: {} — Stripe Webhook rate limit!", key);
                sendError(httpResponse, 1, "stripe-webhook", "stripe");
                return;
            }
        } else if (path.startsWith("/api/creator/stripe") || path.startsWith("/api/payments") || path.startsWith("/api/monetization")) {
            // Stripe Onboarding / Payments / Monetization: 10 attempts per minute per creator/IP
            String key = "rl:endpoint:payments:" + userKey;
            if (!tryConsume(key, 10)) {
                log.warn("RATE LIMIT EXCEEDED: {} — Monetization rate limit. UA: {}", key, userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.RAPID_TIPPING, "Monetization rate limit exceeded for key: " + userKey);
                sendError(httpResponse, 60, "monetization", userKey);
                return;
            }
        } else if (path.startsWith("/api/chat")) {
            // Chat: relaxed limit (200/min)
            String key = "rl:endpoint:chat:" + userKey;
            if (!tryConsume(key, 200)) {
                log.warn("RATE LIMIT EXCEEDED: {} — Chat rate limit.", key);
                sendError(httpResponse, 1, "chat", userKey);
                return;
            }
        } else {
            // General API limit based on role
            int capacity = getLimitForRole(authentication);
            String key = userKey + ":general";
            if (!tryConsume(key, capacity)) {
                log.warn("RATE LIMIT EXCEEDED: {} — General API rate limit. IP: {}, limit: {}", key, maskIp(clientIp), capacity);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.SUSPICIOUS_API_USAGE, "General API rate limit exceeded for key: " + userKey);
                sendError(httpResponse, 60, "general", userKey);
                return;
            }
        }

        chain.doFilter(httpRequest, httpResponse);
    }

    /**
     * Returns true (allow) when the counter is within the limit, false (deny) when exceeded.
     *
     * <p>Uses {@link com.joinlivora.backend.resilience.RedisCircuitBreakerService#execute} so the
     * circuit breaker can transition to HALF_OPEN and probe Redis recovery — the old {@code isOpen()}
     * pre-check blocked that probe path. On circuit-open the fallback ({@code true}) fails open for
     * general API traffic; callers should override the fallback via the 3-arg overload when
     * fail-CLOSED semantics are needed (e.g., login brute-force guard).</p>
     */
    private boolean tryConsume(String key, int limit) {
        Long count = (redisCircuitBreaker != null)
                ? redisCircuitBreaker.execute(
                        () -> redisTemplate.opsForValue().increment(key),
                        null,
                        "redis:rate-limit:" + key)
                : redisTemplate.opsForValue().increment(key);
        if (count == null) {
            // Redis unavailable (circuit open or null response) — fail open for general API
            log.warn("RATE_LIMIT: Redis unavailable for key={}, failing open", key);
            if (metricsService != null) metricsService.getRedisFailuresTotal().increment();
            return true;
        }
        if (count == 1L) {
            // First hit in this window — set TTL (best-effort, non-critical if this fails)
            if (redisCircuitBreaker != null) {
                redisCircuitBreaker.execute(
                        () -> redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS),
                        Boolean.FALSE,
                        "redis:rate-limit:expire");
            } else {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        }
        if (metricsService != null) metricsService.getRateLimitHits().increment();
        if (count > limit) {
            if (metricsService != null) metricsService.getRateLimitBlocked().increment();
            return false;
        }
        return true;
    }

    private int getLimitForRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return anonymousLimit;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.equals("ROLE_ADMIN")) return adminLimit;
            if (role.equals("ROLE_CREATOR")) return creatorLimit;
        }

        return authenticatedLimit;
    }

    private void sendError(HttpServletResponse response, long retryAfterSeconds, String action, String key) throws IOException {
        log.warn("SECURITY: Rate limit exceeded for action: {} from key: {}", action, key);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again later.\"}");
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private String maskIp(String ip) {
        if (ip == null) return "null";
        return ip.replaceAll("(\\d+)\\.(\\d+)\\..*", "$1.$2.***.***");
    }

    // Visible for testing — no-op since state now lives in Redis (mocked in tests)
    void resetRateLimits() {
        // Redis state is reset by the test via mock stubs; nothing to clear locally
    }
}

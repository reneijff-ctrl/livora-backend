package com.joinlivora.backend.security;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final int MAX_BUCKETS = 10000;
    private final AbuseDetectionService abuseDetectionService;

    @Value("${livora.security.rate-limit.anonymous:30}")
    private int anonymousLimit;

    @Value("${livora.security.rate-limit.authenticated:200}")
    private int authenticatedLimit;

    @Value("${livora.security.rate-limit.creator:1000}")
    private int creatorLimit;

    @Value("${livora.security.rate-limit.admin:5000}")
    private int adminLimit;

    @Value("${livora.security.rate-limit.websocket:10000}")
    private int websocketLimit;

    @Value("${livora.security.rate-limit.login:5}")
    private int loginLimit;

    @Value("${livora.security.rate-limit.register:3}")
    private int registerLimit;

    public RateLimitingFilter(AbuseDetectionService abuseDetectionService) {
        this.abuseDetectionService = abuseDetectionService;
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
        
        String userKey = isAuthenticated ? authentication.getName() : clientIp;
        
        // WebSocket connections get higher limits
        if (path.startsWith("/ws")) {
            if (!tryConsume(userKey + ":ws", websocketLimit, Duration.ofMinutes(1))) {
                log.warn("SECURITY [rate_limit]: WebSocket rate limit exceeded for {}. IP: {}", userKey, maskIp(clientIp));
                sendError(httpResponse, 1, "websocket", userKey);
                return;
            }
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        // Define rate limits per endpoint category
        if (path.startsWith("/hls/")) {
            // HLS content has its own rate limiting in HlsProxyController
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        if (path.startsWith("/auth/login") || path.startsWith("/api/auth/login")) {
            // Login: 5 attempts per minute per IP (strict)
            if (!tryConsume(clientIp + ":login", loginLimit, Duration.ofMinutes(1))) {
                log.warn("SECURITY [security_incident]: Rate limit exceeded for LOGIN. IP: {}, UA: {}", maskIp(clientIp), userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.LOGIN_BRUTE_FORCE, "Login rate limit exceeded");
                sendError(httpResponse, 60, "login", clientIp);
                return;
            }
        } else if (path.startsWith("/auth/register") || path.startsWith("/api/auth/register")) {
            // Register: 3 attempts per minute per IP
            if (!tryConsume(clientIp + ":register", registerLimit, Duration.ofMinutes(1))) {
                log.warn("SECURITY: Rate limit exceeded for REGISTER. IP: {}, UA: {}", maskIp(clientIp), userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.SUSPICIOUS_API_USAGE, "Register rate limit exceeded");
                sendError(httpResponse, 60, "register", clientIp);
                return;
            }
        } else if (path.startsWith("/webhooks/stripe") || path.startsWith("/api/payments/webhook") || path.startsWith("/api/stripe/webhook")) {
            // Stripe Webhooks: higher limit to avoid dropping events during spikes
            if (!tryConsume("stripe-webhook", 100, Duration.ofMinutes(1))) {
                log.error("SECURITY: Rate limit exceeded for Stripe Webhook!");
                sendError(httpResponse, 1, "stripe-webhook", "stripe");
                return;
            }
        } else if (path.startsWith("/api/creator/stripe") || path.startsWith("/api/payments") || path.startsWith("/api/monetization")) {
            // Stripe Onboarding / Payments / Monetization: 10 attempts per minute per creator/IP
            if (!tryConsume(userKey + ":monetization", 10, Duration.ofMinutes(1))) {
                log.warn("SECURITY: Rate limit exceeded for MONETIZATION. User/IP: {}, UA: {}", userKey, userAgent);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.RAPID_TIPPING, "Monetization rate limit exceeded for key: " + userKey);
                sendError(httpResponse, 60, "monetization", userKey);
                return;
            }
        } else {
            // General API limit based on role
            int capacity = getLimitForRole(authentication);
            if (!tryConsume(userKey + ":general", capacity, Duration.ofMinutes(1))) {
                log.warn("SECURITY [rate_limit]: General API rate limit exceeded for {}. IP: {}, Limit: {}", userKey, maskIp(clientIp), capacity);
                abuseDetectionService.trackEvent(null, clientIp, AbuseEventType.SUSPICIOUS_API_USAGE, "General API rate limit exceeded for key: " + userKey);
                sendError(httpResponse, 60, "general", userKey);
                return;
            }
        }

        chain.doFilter(httpRequest, httpResponse);
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

    private boolean tryConsume(String key, int capacity, Duration period) {
        if (buckets.size() >= MAX_BUCKETS && !buckets.containsKey(key)) {
            log.warn("SECURITY: Rate limiting bucket map full! Rejecting new bucket for key: {}", key);
            return false;
        }
        
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, period)
                        .build())
                .build());
        return bucket.tryConsume(1);
    }

    /**
     * Periodically clear rate limit buckets to prevent memory leaks.
     * Runs every hour.
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupBuckets() {
        log.info("Running scheduled cleanup of rate limiting buckets. Current size: {}", buckets.size());
        buckets.clear();
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

    // Visible for testing
    void resetRateLimits() {
        buckets.clear();
    }
}

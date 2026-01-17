package com.joinlivora.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String clientIp = getClientIP(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        boolean isAuthenticated = httpRequest.getUserPrincipal() != null;
        String userKey = isAuthenticated ? httpRequest.getUserPrincipal().getName() : clientIp;
        
        // Define rate limits per endpoint category
        if (path.startsWith("/auth/login") || path.startsWith("/api/auth/login")) {
            // Login: 5 attempts per minute per IP (strict)
            if (!tryConsume(clientIp + ":login", 5, Duration.ofMinutes(1))) {
                log.warn("SECURITY [security_incident]: Rate limit exceeded for LOGIN. IP: {}, UA: {}", clientIp, userAgent);
                sendError(httpResponse, 60, "login", clientIp);
                return;
            }
        } else if (path.startsWith("/auth/refresh") || path.startsWith("/api/auth/refresh")) {
            // Refresh: 10 attempts per minute per token/IP
            if (!tryConsume(clientIp + ":refresh", 10, Duration.ofMinutes(1))) {
                log.warn("SECURITY: Rate limit exceeded for REFRESH. IP: {}, UA: {}", clientIp, userAgent);
                sendError(httpResponse, 60, "refresh", clientIp);
                return;
            }
        } else if (path.startsWith("/api/payments")) {
            // Payments: 3 attempts per minute per user/IP
            if (path.startsWith("/api/payments/webhook")) {
                // Stripe Webhooks: higher limit to avoid dropping events during spikes
                if (!tryConsume("stripe-webhook", 100, Duration.ofMinutes(1))) {
                    log.error("SECURITY: Rate limit exceeded for Stripe Webhook!");
                    sendError(httpResponse, 1, "stripe-webhook", "stripe");
                    return;
                }
            } else {
                if (!tryConsume(userKey + ":payments", 3, Duration.ofMinutes(1))) {
                    log.warn("SECURITY: Rate limit exceeded for PAYMENTS. User/IP: {}, UA: {}", userKey, userAgent);
                    sendError(httpResponse, 60, "payments", userKey);
                    return;
                }
            }
        } else if (path.startsWith("/auth/register") || path.startsWith("/api/auth/register")) {
            // Register: 3 attempts per minute per IP
            if (!tryConsume(clientIp + ":register", 3, Duration.ofMinutes(1))) {
                log.warn("SECURITY: Rate limit exceeded for REGISTER. IP: {}, UA: {}", clientIp, userAgent);
                sendError(httpResponse, 60, "register", clientIp);
                return;
            }
        } else {
            // General API limit
            int capacity = isAuthenticated ? 100 : 20; 
            if (!tryConsume(userKey + ":general", capacity, Duration.ofMinutes(1))) {
                sendError(httpResponse, 60, "general", userKey);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @SuppressWarnings("deprecation")
    private boolean tryConsume(String key, int capacity, Duration period) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, period)))
                .build());
        return bucket.tryConsume(1);
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
}

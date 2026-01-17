package com.joinlivora.backend.analytics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FunnelTrackingFilter extends OncePerRequestFilter {

    private final AnalyticsEventPublisher analyticsEventPublisher;
    private static final String FUNNEL_COOKIE_NAME = "livora_funnel_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Only track "page visits" on certain paths or common entry points
        // In a SPA, the backend usually only sees API calls, but the landing page might serve static index.html
        if (isPageVisit(path)) {
            String funnelId = getOrCreateFunnelId(request, response);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("path", path);
            metadata.put("userAgent", request.getHeader("User-Agent"));
            metadata.put("referrer", request.getHeader("Referer"));
            metadata.put("ip", anonymizeIp(getClientIP(request)));
            
            // Note: User is likely null here as this filter might run before auth
            analyticsEventPublisher.publishEvent(AnalyticsEventType.VISIT, null, funnelId, metadata);
        }

        filterChain.doFilter(request, response);
    }

    private String anonymizeIp(String ip) {
        if (ip == null) return "0.0.0.0";
        if (ip.contains(".")) {
            // IPv4: mask last octet
            int lastDot = ip.lastIndexOf(".");
            return ip.substring(0, lastDot) + ".0";
        } else if (ip.contains(":")) {
            // IPv6: mask last groups
            int lastColon = ip.lastIndexOf(":");
            return ip.substring(0, lastColon) + ":0000";
        }
        return "anonymized";
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private boolean isPageVisit(String path) {
        // We track the root and potential entry points
        return path.equals("/") || path.equals("/index.html") || path.startsWith("/pricing") || path.startsWith("/register");
    }

    private String getOrCreateFunnelId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (FUNNEL_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        String newFunnelId = UUID.randomUUID().toString();
        Cookie funnelCookie = new Cookie(FUNNEL_COOKIE_NAME, newFunnelId);
        funnelCookie.setPath("/");
        funnelCookie.setHttpOnly(false); // Accessible by JS if needed for frontend analytics
        funnelCookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
        // Secure and SameSite handled by general cookie policy or infrastructure
        response.addCookie(funnelCookie);
        return newFunnelId;
    }
}

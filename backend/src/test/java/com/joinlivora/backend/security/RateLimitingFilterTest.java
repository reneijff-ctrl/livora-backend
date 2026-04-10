package com.joinlivora.backend.security;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.config.MetricsService;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private AbuseDetectionService abuseDetectionService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private Counter mockCounter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter rateLimitingFilter;

    /** Per-key counter backing the Redis INCR mock. Cleared in setUp(). */
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        counters.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Simulate Redis INCR: returns 1 on first call, 2 on second, etc.
        when(valueOps.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        });

        lenient().when(metricsService.getRedisFailuresTotal()).thenReturn(mockCounter);
        lenient().when(metricsService.getRateLimitHits()).thenReturn(mockCounter);
        lenient().when(metricsService.getRateLimitBlocked()).thenReturn(mockCounter);

        rateLimitingFilter = new RateLimitingFilter(redisTemplate, abuseDetectionService, metricsService);
        rateLimitingFilter.resetRateLimits();

        lenient().when(request.getHeader("User-Agent")).thenReturn("Test UA");
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        // Initialise @Value fields
        ReflectionTestUtils.setField(rateLimitingFilter, "anonymousLimit", 30);
        ReflectionTestUtils.setField(rateLimitingFilter, "authenticatedLimit", 100);
        ReflectionTestUtils.setField(rateLimitingFilter, "creatorLimit", 200);
        ReflectionTestUtils.setField(rateLimitingFilter, "adminLimit", 5000);
        ReflectionTestUtils.setField(rateLimitingFilter, "websocketLimit", 1000);
        ReflectionTestUtils.setField(rateLimitingFilter, "loginLimit", 5);
        ReflectionTestUtils.setField(rateLimitingFilter, "registerLimit", 3);
    }

    @Test
    void doFilterInternal_LoginRateLimit_ShouldTrackEvent() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        // 6 requests: first 5 allowed, 6th exceeds loginLimit=5
        for (int i = 0; i < 6; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, atLeastOnce()).setStatus(429);
        verify(abuseDetectionService).trackEvent(isNull(), eq("1.2.3.4"), eq(AbuseEventType.LOGIN_BRUTE_FORCE), anyString());
    }

    @Test
    void doFilterInternal_GeneralRateLimit_ShouldTrackEvent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/something");
        when(request.getRemoteAddr()).thenReturn("5.6.7.8");

        // 31 requests: first 30 allowed, 31st exceeds anonymousLimit=30
        for (int i = 0; i < 31; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, atLeastOnce()).setStatus(429);
        verify(abuseDetectionService).trackEvent(isNull(), eq("5.6.7.8"), eq(AbuseEventType.SUSPICIOUS_API_USAGE), anyString());
    }

    @Test
    void doFilterInternal_UnderLimit_ShouldNotTrackEvent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/something");
        when(request.getRemoteAddr()).thenReturn("9.10.11.12");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(abuseDetectionService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void doFilterInternal_WebSocketRateLimit_ShouldUseHighLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/ws/chat");
        when(request.getRemoteAddr()).thenReturn("10.11.12.13");

        // 500 requests well under websocketLimit=1000 — none should be rejected
        for (int i = 0; i < 500; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, never()).setStatus(429);
        verify(filterChain, times(500)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AdminRateLimit_ShouldUseHighLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/admin/stats");
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");

        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("admin@test.com");
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(auth).getAuthorities();

        org.springframework.security.core.context.SecurityContext context =
                mock(org.springframework.security.core.context.SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        try {
            // 500 requests well under adminLimit=5000 — none should be rejected
            for (int i = 0; i < 500; i++) {
                rateLimitingFilter.doFilterInternal(request, response, filterChain);
            }

            verify(response, never()).setStatus(429);
            verify(filterChain, times(500)).doFilter(request, response);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void doFilterInternal_CreatorRateLimit_ShouldUseMediumLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/creators/content");
        when(request.getRemoteAddr()).thenReturn("2.2.2.2");

        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("creator@test.com");
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR"));
        doReturn(authorities).when(auth).getAuthorities();

        org.springframework.security.core.context.SecurityContext context =
                mock(org.springframework.security.core.context.SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        try {
            // 200 requests well under creatorLimit=200 (boundary: exactly at limit — all allowed)
            for (int i = 0; i < 200; i++) {
                rateLimitingFilter.doFilterInternal(request, response, filterChain);
            }

            verify(response, never()).setStatus(429);
            verify(filterChain, times(200)).doFilter(request, response);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void doFilterInternal_RedisReturnsNull_ShouldFailOpen() throws Exception {
        // Simulate Redis being unavailable (increment returns null)
        when(valueOps.increment(anyString())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should fail open: request passes through
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }
}

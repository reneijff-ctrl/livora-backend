package com.joinlivora.backend.security;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private AbuseDetectionService abuseDetectionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(request.getHeader("User-Agent")).thenReturn("Test UA");
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        
        // Initialize @Value fields
        ReflectionTestUtils.setField(rateLimitingFilter, "anonymousLimit", 30);
        ReflectionTestUtils.setField(rateLimitingFilter, "authenticatedLimit", 200);
        ReflectionTestUtils.setField(rateLimitingFilter, "creatorLimit", 1000);
        ReflectionTestUtils.setField(rateLimitingFilter, "adminLimit", 5000);
        ReflectionTestUtils.setField(rateLimitingFilter, "websocketLimit", 10000);
        ReflectionTestUtils.setField(rateLimitingFilter, "loginLimit", 5);
        ReflectionTestUtils.setField(rateLimitingFilter, "registerLimit", 3);
    }

    @Test
    void doFilterInternal_LoginRateLimit_ShouldTrackEvent() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        // Exceed limit (limit is 5)
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

        // Exceed limit (limit is 30 for unauthenticated)
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

        // Should be able to make 1000 requests without violation (limit is 10000)
        for (int i = 0; i < 1000; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, never()).setStatus(429);
        verify(filterChain, times(1000)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AdminRateLimit_ShouldUseHighLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/admin/stats");
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("admin@test.com");
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = 
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(auth).getAuthorities();
        
        org.springframework.security.core.context.SecurityContext context = mock(org.springframework.security.core.context.SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        try {
            // Should be able to make 500 requests without violation (limit is 5000)
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
        
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("creator@test.com");
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = 
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR"));
        doReturn(authorities).when(auth).getAuthorities();
        
        org.springframework.security.core.context.SecurityContext context = mock(org.springframework.security.core.context.SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        try {
            // Should be able to make 200 requests without violation (limit is 1000)
            for (int i = 0; i < 200; i++) {
                rateLimitingFilter.doFilterInternal(request, response, filterChain);
            }

            verify(response, never()).setStatus(429);
            verify(filterChain, times(200)).doFilter(request, response);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}









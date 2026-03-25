package com.joinlivora.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRiskStateRepository userRiskStateRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        lenient().when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void shouldNotFilter_WhenPublicContentPath_ShouldReturnTrue() {
        when(request.getServletPath()).thenReturn("/api/auth/content/public/something");
        
        org.junit.jupiter.api.Assertions.assertTrue(jwtAuthenticationFilter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_WhenWebhookPath_ShouldReturnFalse() {
        when(request.getServletPath()).thenReturn("/api/webhooks/stripe");
        
        org.junit.jupiter.api.Assertions.assertFalse(jwtAuthenticationFilter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_WhenOtherPath_ShouldReturnFalse() {
        when(request.getServletPath()).thenReturn("/api/user/me");
        
        org.junit.jupiter.api.Assertions.assertFalse(jwtAuthenticationFilter.shouldNotFilter(request));
    }

    @Test
    void doFilterInternal_WhenUserBlocked_ShouldReturn403() throws Exception {
        String token = "valid-token";
        String email = "blocked@test.com";
        Long userId = 1L;
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(email);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(claims);
        
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserRiskState blockedState = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(Instant.now().plusSeconds(3600))
                .build();
        
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.of(blockedState));
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_WhenUserNotBlocked_ShouldProceed() throws Exception {
        String token = "valid-token";
        String email = "active@test.com";
        Long userId = 2L;
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.get("role", String.class)).thenReturn("USER");
        when(claims.getIssuedAt()).thenReturn(java.util.Date.from(Instant.now()));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(claims);
        
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenUserSoftBlocked_ShouldStillProceed() throws Exception {
        String token = "valid-token";
        String email = "softblocked@test.com";
        Long userId = 3L;
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.get("role", String.class)).thenReturn("USER");
        when(claims.getIssuedAt()).thenReturn(java.util.Date.from(Instant.now()));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(claims);
        
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Mock soft block
        lenient().when(abuseDetectionService.isSoftBlocked(any(), any())).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Should still proceed because it's silent
        verify(filterChain).doFilter(request, response);
        verify(abuseDetectionService, atLeastOnce()).isSoftBlocked(any(), any());
    }

    @Test
    void doFilterInternal_WhenUserTempSuspended_ShouldReturn403() throws Exception {
        String token = "valid-token";
        String email = "suspended@test.com";
        Long userId = 4L;
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.getIssuedAt()).thenReturn(java.util.Date.from(Instant.now()));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(claims);
        
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());

        com.joinlivora.backend.abuse.model.UserRestriction restriction = com.joinlivora.backend.abuse.model.UserRestriction.builder()
                .restrictionLevel(com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.of(restriction));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        
        // Check if response contains expiresAt
        String jsonResponse = stringWriter.toString();
        org.junit.jupiter.api.Assertions.assertTrue(jsonResponse.contains("USER_RESTRICTED_TEMP_SUSPENSION"));
        org.junit.jupiter.api.Assertions.assertTrue(jsonResponse.contains("expiresAt"));
    }
}









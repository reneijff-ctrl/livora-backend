package com.joinlivora.backend.auth;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.auth.dto.LoginRequest;
import com.joinlivora.backend.auth.dto.LoginResponse;
import com.joinlivora.backend.exception.TrustChallengeException;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.payment.SubscriptionService;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RefreshTokenService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserService userService;
    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private VelocityTrackerService velocityTrackerService;
    @Mock
    private TrustEvaluationService trustEvaluationService;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private com.joinlivora.backend.security.LoginSuccessHandler loginSuccessHandler;
    @Mock
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;
    @Mock
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private org.springframework.core.env.Environment env;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setRole(Role.USER);

        lenient().when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.empty());
        lenient().when(env.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Test
    void login_Success_ShouldTrackVelocity() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("password");

        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        Authentication auth = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@test.com");
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.create(any())).thenReturn(new com.joinlivora.backend.security.RefreshToken());

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);
        lenient().when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        LoginResponse response = authService.login(request, "funnel-1", httpRequest);

        assertNotNull(response);
        // Verify trust evaluation happens BEFORE authentication
        verify(trustEvaluationService).evaluate(user, null, "127.0.0.1");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(velocityTrackerService).trackAction(eq(1L), eq(VelocityActionType.LOGIN));
        verify(loginSuccessHandler).onLoginSuccess(eq(user), eq(httpRequest));
    }

    @Test
    void login_WhenTrustChallenge_ShouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(TrustChallengeException.class, () ->
                authService.login(request, "f1", httpRequest)
        );

        verify(loginFailureHandler).onLoginFailure(eq(user), eq("trust_challenge_required"), eq(httpRequest));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_WhenTrustBlock_ShouldLogSignalAndThrow() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).build());

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                authService.login(request, "f1", httpRequest)
        );

        verify(loginFailureHandler).onLoginFailure(eq(user), eq("trust_evaluation_block"), eq(httpRequest));
        verify(fraudDetectionService).logFraudSignal(eq(1L), eq(FraudDecisionLevel.HIGH), eq(com.joinlivora.backend.fraud.model.FraudSource.LOGIN), eq(com.joinlivora.backend.fraud.model.FraudSignalType.TRUST_EVALUATION_BLOCK), anyString());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_WhenSuspended_ShouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.SUSPENDED);

        when(userService.getByEmail("test@test.com")).thenReturn(user);
        lenient().when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.DisabledException.class, () ->
                authService.login(request, "f1", httpRequest)
        );
        verify(loginFailureHandler).onLoginFailure(eq(user), eq("account_restricted"), eq(httpRequest));
    }

    @Test
    void login_WhenTempSuspended_ShouldThrowUserRestrictedException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        
        com.joinlivora.backend.abuse.model.UserRestriction restriction = com.joinlivora.backend.abuse.model.UserRestriction.builder()
                .restrictionLevel(com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION)
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
        when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.of(restriction));

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);

        com.joinlivora.backend.exception.UserRestrictedException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.joinlivora.backend.exception.UserRestrictedException.class, 
                () -> authService.login(request, "f1", httpRequest)
        );
        
        org.junit.jupiter.api.Assertions.assertEquals(com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION, ex.getLevel());
        verify(loginFailureHandler).onLoginFailure(eq(user), eq("temp_suspension"), eq(httpRequest));
    }

    @Test
    void login_BadCredentials_ShouldCallAbuseDetection() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("wrong-password");

        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        when(authenticationManager.authenticate(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getHeader(anyString())).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                authService.login(request, "f1", httpRequest)
        );

        verify(userService).incrementFailedAttempts(eq(user));
        verify(abuseDetectionService).checkLoginBruteForce(any(java.util.UUID.class), eq("127.0.0.1"));
    }

    @Test
    void refresh_WhenTempSuspended_ShouldThrowUserRestrictedException() {
        com.joinlivora.backend.auth.dto.RefreshTokenRequest request = new com.joinlivora.backend.auth.dto.RefreshTokenRequest();
        request.setRefreshToken("old-token");
        
        com.joinlivora.backend.security.RefreshToken refreshToken = new com.joinlivora.backend.security.RefreshToken();
        refreshToken.setUser(user);
        when(refreshTokenService.rotateRefreshToken("old-token")).thenReturn(refreshToken);
        
        com.joinlivora.backend.abuse.model.UserRestriction restriction = com.joinlivora.backend.abuse.model.UserRestriction.builder()
                .restrictionLevel(com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION)
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
        when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.of(restriction));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.joinlivora.backend.exception.UserRestrictedException.class, 
                () -> authService.refresh(request)
        );
    }

    @Test
    void register_shouldPublishEvent() {
        com.joinlivora.backend.auth.dto.RegisterRequest request = new com.joinlivora.backend.auth.dto.RegisterRequest();
        request.setEmail("new@test.com");
        request.setPassword("password");

        when(userService.registerUser(anyString(), anyString())).thenReturn(user);

        authService.register(request, "funnel-1", "127.0.0.1", "US");

        verify(eventPublisher).publishEvent(any(com.joinlivora.backend.email.event.UserRegisteredEvent.class));
    }

    @Test
    void requestPasswordReset_shouldPublishEvent() {
        when(userService.getByEmail("test@test.com")).thenReturn(user);

        authService.requestPasswordReset("test@test.com");

        verify(eventPublisher).publishEvent(any(com.joinlivora.backend.email.event.PasswordResetRequestedEvent.class));
    }

    @Test
    void verifyEmail_Success() {
        user.setEmailVerificationToken("valid-token");
        when(userRepository.findByEmailVerificationToken("valid-token")).thenReturn(java.util.Optional.of(user));

        authService.verifyEmail("valid-token");

        assertTrue(user.isEmailVerified());
        assertNull(user.getEmailVerificationToken());
        verify(userRepository).save(user);
    }

    @Test
    void resendVerification_Success() {
        when(userService.getByEmail("test@test.com")).thenReturn(user);

        authService.resendVerification("test@test.com");

        assertNotNull(user.getEmailVerificationToken());
        verify(userService).updateUser(user);
        verify(eventPublisher).publishEvent(any(com.joinlivora.backend.email.event.EmailVerificationRequestedEvent.class));
    }
}









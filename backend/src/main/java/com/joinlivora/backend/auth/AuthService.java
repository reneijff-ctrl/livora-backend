package com.joinlivora.backend.auth;

import com.joinlivora.backend.admin.service.AdminPermissionService;
import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.auth.dto.*;
import com.joinlivora.backend.auth.event.UserLogoutEvent;
import com.joinlivora.backend.exception.TrustChallengeException;
import com.joinlivora.backend.security.LoginFailureHandler;
import com.joinlivora.backend.security.LoginSuccessHandler;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.payment.SubscriptionService;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RefreshToken;
import com.joinlivora.backend.security.RefreshTokenService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.email.event.PasswordResetRequestedEvent;
import com.joinlivora.backend.email.event.UserRegisteredEvent;
import com.joinlivora.backend.email.event.EmailVerificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SubscriptionService subscriptionService;
    private final VelocityTrackerService velocityTrackerService;
    private final TrustEvaluationService trustEvaluationService;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final AuditLogoutHandler auditLogoutHandler;
    private final com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final CreatorProfileService creatorProfileService;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final AdminPermissionService adminPermissionService;
    private final Environment env;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserService userService,
            com.joinlivora.backend.user.UserRepository userRepository,
            AnalyticsEventPublisher analyticsEventPublisher,
            SubscriptionService subscriptionService,
            VelocityTrackerService velocityTrackerService,
            TrustEvaluationService trustEvaluationService,
            FraudDetectionService fraudDetectionService,
            AuditService auditService,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            AuditLogoutHandler auditLogoutHandler,
            com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService,
            CreatorProfileService creatorProfileService,
            ApplicationEventPublisher eventPublisher,
            AdminRealtimeEventService adminRealtimeEventService,
            AdminPermissionService adminPermissionService,
            Environment env
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.subscriptionService = subscriptionService;
        this.velocityTrackerService = velocityTrackerService;
        this.trustEvaluationService = trustEvaluationService;
        this.fraudDetectionService = fraudDetectionService;
        this.auditService = auditService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.auditLogoutHandler = auditLogoutHandler;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
        this.creatorProfileService = creatorProfileService;
        this.eventPublisher = eventPublisher;
        this.adminRealtimeEventService = adminRealtimeEventService;
        this.adminPermissionService = adminPermissionService;
        this.env = env;
    }

    public void register(RegisterRequest request, String funnelId, String ip, String country) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getPassword()
        );

        // Generate email verification token
        String token = java.util.UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerified(false);
        userService.updateUser(user);

        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.USER_REGISTERED,
                user,
                funnelId,
                Map.of(
                        "email", user.getEmail(),
                        "ip", ip != null ? ip : "",
                        "country", country != null ? country : ""
                )
        );

        // Trigger transactional email
        eventPublisher.publishEvent(new UserRegisteredEvent(this, user));

        // Broadcast to admins
        adminRealtimeEventService.broadcastUserRegistered(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Invalid verification token"));
        
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        
        logger.info("Email verified for user: {}", user.getEmail());
    }

    public void resendVerification(String email) {
        User user = userService.getByEmail(email);
        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }

        // Generate new token
        String token = java.util.UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        userService.updateUser(user);

        logger.info("Resending verification email to: {}", email);
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(this, user));
    }

    public void requestPasswordReset(String email) {
        try {
            User user = userService.getByEmail(email);
            // Generate a random token (in a real app, this would be stored in the DB with an expiry)
            String token = java.util.UUID.randomUUID().toString();
            
            logger.info("Password reset requested for email: {}", email);
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(this, user, token));
        } catch (com.joinlivora.backend.exception.ResourceNotFoundException e) {
            logger.warn("Password reset requested for non-existent email: {}", email);
            // Do nothing, returning success to avoid user enumeration
        }
    }

    @CacheEvict(value = {"users", "subscriptions"}, key = "#request.email")
    public LoginResponse login(LoginRequest request, String funnelId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user;
        try {
            user = userService.getByEmail(request.getEmail());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("User not found")) {
                loginFailureHandler.onLoginFailure(request.getEmail(), "user_not_found", httpRequest);
            }
            throw e;
        }
        String ip = com.joinlivora.backend.util.RequestUtil.getClientIP(httpRequest);

        if (!isDevProfile()) {
            if (user.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED ||
                user.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED) {
                logger.warn("SECURITY: Login attempt for SUSPENDED/TERMINATED account: {}", user.getEmail());
                loginFailureHandler.onLoginFailure(user, "account_restricted", httpRequest);
                throw new org.springframework.security.authentication.DisabledException("Account is restricted.");
            }

            // Check for active TEMP_SUSPENSION restriction
            java.util.Optional<com.joinlivora.backend.abuse.model.UserRestriction> activeRestriction = restrictionService.getActiveRestriction(new java.util.UUID(0L, user.getId()));
            if (activeRestriction.isPresent() && activeRestriction.get().getRestrictionLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION) {
                logger.warn("SECURITY: Login attempt for TEMP_SUSPENDED account: {}", user.getEmail());
                loginFailureHandler.onLoginFailure(user, "temp_suspension", httpRequest);
                throw new com.joinlivora.backend.exception.UserRestrictedException(
                        com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION,
                        "Your account is temporarily suspended.",
                        activeRestriction.get().getExpiresAt()
                );
            }

            // Perform trust evaluation BEFORE authentication
            String fingerprintHash = com.joinlivora.backend.util.RequestUtil.getDeviceFingerprint(httpRequest);
            RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ip);
            if (result.getDecision() == RiskDecision.BLOCK) {
                logger.warn("SECURITY [trust_evaluation]: Blocked login attempt for creator: {} from IP: {} with fingerprint: {}. ExplanationId: {}",
                        user.getEmail(), ip, fingerprintHash, result.getExplanationId());
                fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.HIGH, FraudSource.LOGIN, FraudSignalType.TRUST_EVALUATION_BLOCK, "TRUST_EVALUATION_BLOCK");
                loginFailureHandler.onLoginFailure(user, "trust_evaluation_block", httpRequest);
                throw new org.springframework.security.access.AccessDeniedException("Login blocked due to high security risk.");
            } else if (result.getDecision() == RiskDecision.REVIEW) {
                logger.info("LOGIN BLOCKED – VERIFICATION REQUIRED");
                logger.info("SECURITY [trust_evaluation]: Trust challenge required for creator: {}. ExplanationId: {}", user.getEmail(), result.getExplanationId());
                loginFailureHandler.onLoginFailure(user, "trust_challenge_required", httpRequest);
                throw new TrustChallengeException("Additional verification required.");
            }

            if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
                logger.warn("SECURITY: Login attempt for locked account: {}", user.getEmail());
                analyticsEventPublisher.publishEvent(
                        AnalyticsEventType.USER_LOGIN_FAILED,
                        user,
                        funnelId,
                        Map.of("type", "account_locked")
                );
                loginFailureHandler.onLoginFailure(user, "account_locked", httpRequest);
                throw new LockedException("Account is temporarily locked due to multiple failed login attempts. Please try again later.");
            }
        }

        try {
            var auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            userService.resetFailedAttempts(user.getEmail());

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            User authenticatedUser = userService.getByEmail(userDetails.getUsername());

            // Ensure creator profile exists for all creators on login
            if (authenticatedUser.getRole() == Role.CREATOR) {
                creatorProfileService.initializeCreatorProfile(authenticatedUser);
            }

            // Track LOGIN velocity AFTER authentication and BEFORE returning response
            velocityTrackerService.trackAction(authenticatedUser.getId(), VelocityActionType.LOGIN);

            String accessToken = jwtService.generateAccessToken(authenticatedUser);

            Instant expiresAt = Instant.now().plusSeconds(jwtService.getJwtExpiration());

            RefreshToken refreshToken = refreshTokenService.create(authenticatedUser);

            logger.info("LOGIN SUCCESS – JWT ISSUED");
            logger.info("SECURITY: Login successful for creator: {}", authenticatedUser.getEmail());
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.USER_LOGIN_SUCCESS,
                    authenticatedUser,
                    funnelId,
                    Map.of(
                            "ip", ip != null ? ip : "",
                            "country", com.joinlivora.backend.util.RequestUtil.getClientCountry(httpRequest) != null ? com.joinlivora.backend.util.RequestUtil.getClientCountry(httpRequest) : ""
                    )
            );

            loginSuccessHandler.onLoginSuccess(authenticatedUser, httpRequest);

            com.joinlivora.backend.security.UserPrincipal principal = new com.joinlivora.backend.security.UserPrincipal(authenticatedUser);
            String adminRoleName = authenticatedUser.getAdminRole() != null ? authenticatedUser.getAdminRole().name() : null;
            List<String> permissions = adminPermissionService.getPermissions(principal).stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            return new LoginResponse(
                    accessToken,
                    refreshToken.getPlainToken(),
                    expiresAt,
                    authenticatedUser.getRole().name(),
                    authenticatedUser.getId(),
                    authenticatedUser.getEmail(),
                    authenticatedUser.getUsername(),
                    adminRoleName,
                    permissions
            );
        } catch (BadCredentialsException e) {
            userService.incrementFailedAttempts(user);
            logger.warn("SECURITY: Login failure for creator: {}. Attempt: {}", user.getEmail(), user.getFailedLoginAttempts() + 1);
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.USER_LOGIN_FAILED,
                    user,
                    funnelId,
                    Map.of("type", "bad_credentials", "attempt", user.getFailedLoginAttempts())
            );
            loginFailureHandler.onLoginFailure(user, "bad_credentials", httpRequest);
            abuseDetectionService.checkLoginBruteForce(new java.util.UUID(0L, user.getId()), ip);
            throw e;
        }
    }

    @Transactional
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new com.joinlivora.backend.exception.ResourceNotFoundException("Refresh token is missing");
        }

        RefreshToken newRefreshToken;
        try {
            newRefreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        } catch (Exception e) {
            logger.warn("SECURITY: Token refresh failed — invalid or expired token");
            throw new com.joinlivora.backend.exception.ResourceNotFoundException("Invalid or expired refresh token");
        }

        User user = newRefreshToken.getUser();

        if (!isDevProfile()) {
            if (user.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED ||
                user.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED) {
                logger.warn("SECURITY: Token refresh attempt for SUSPENDED/TERMINATED account: {}", user.getEmail());
                throw new org.springframework.security.authentication.DisabledException("Account is restricted.");
            }

            // Check for active TEMP_SUSPENSION restriction
            java.util.Optional<com.joinlivora.backend.abuse.model.UserRestriction> activeRestriction = restrictionService.getActiveRestriction(new java.util.UUID(0L, user.getId()));
            if (activeRestriction.isPresent() && activeRestriction.get().getRestrictionLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION) {
                logger.warn("SECURITY: Token refresh attempt for TEMP_SUSPENDED account: {}", user.getEmail());
                throw new com.joinlivora.backend.exception.UserRestrictedException(
                        com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION,
                        "Your account is temporarily suspended.",
                        activeRestriction.get().getExpiresAt()
                );
            }
        }

        logger.info("SECURITY: Token refresh executed for creator: {}", user.getEmail());

        String newAccessToken = jwtService.generateAccessToken(user);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken.getPlainToken());
    }

    private boolean isDevProfile() {
        return java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");
    }

    public void logout(String refreshToken, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (refreshToken != null) {
            String email = refreshTokenService.getEmailFromToken(refreshToken);
            if (email != null) {
                auditLogoutHandler.logLogout(email, httpRequest);
                eventPublisher.publishEvent(new UserLogoutEvent(this, email));
                evictAllCaches(email);
            }
            refreshTokenService.revokeToken(refreshToken);
        }
    }

    @CacheEvict(value = {"users", "subscriptions"}, key = "#email")
    public void evictAllCaches(String email) {
        // Just for cache eviction
    }

    public User getUserByEmail(String email) {
        return userService.getByEmail(email);
    }

    public SubscriptionResponse getSubscriptionForUser(User user) {
        return subscriptionService.getSubscriptionForUser(user);
    }
}

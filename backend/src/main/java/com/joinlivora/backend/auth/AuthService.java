package com.joinlivora.backend.auth;

import com.joinlivora.backend.auth.dto.LoginRequest;
import com.joinlivora.backend.auth.dto.LoginResponse;
import com.joinlivora.backend.auth.dto.RefreshTokenRequest;
import com.joinlivora.backend.auth.dto.RegisterRequest;
import com.joinlivora.backend.auth.dto.TokenRefreshResponse;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RefreshToken;
import com.joinlivora.backend.security.RefreshTokenService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    private final com.joinlivora.backend.payment.SubscriptionService subscriptionService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserService userService,
            com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher,
            com.joinlivora.backend.payment.SubscriptionService subscriptionService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.subscriptionService = subscriptionService;
    }

    public void register(RegisterRequest request, String funnelId) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getPassword()
        );
        analyticsEventPublisher.publishEvent(
                com.joinlivora.backend.analytics.AnalyticsEventType.USER_REGISTERED,
                user,
                funnelId,
                java.util.Map.of("email", user.getEmail())
        );
    }

    @CacheEvict(value = {"users", "subscriptions"}, key = "#request.email")
    public LoginResponse login(LoginRequest request, String funnelId) {
        User user = userService.getByEmail(request.getEmail());

        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(java.time.Instant.now())) {
            logger.warn("SECURITY: Login attempt for locked account: {}", user.getEmail());
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.USER_LOGIN_FAILED,
                    user,
                    funnelId,
                    java.util.Map.of("reason", "account_locked")
            );
            throw new org.springframework.security.authentication.LockedException("Account is temporarily locked due to multiple failed login attempts. Please try again later.");
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

            String accessToken = jwtService.generateToken(
                    authenticatedUser.getEmail(),
                    authenticatedUser.getRole().name()
            );

            RefreshToken refreshToken = refreshTokenService.create(authenticatedUser);

            logger.info("SECURITY: Login successful for user: {}", authenticatedUser.getEmail());
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.USER_LOGIN_SUCCESS,
                    authenticatedUser,
                    funnelId,
                    java.util.Map.of()
            );

            return new LoginResponse(accessToken, refreshToken.getToken());
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            userService.incrementFailedAttempts(user);
            logger.warn("SECURITY: Login failure for user: {}. Attempt: {}", user.getEmail(), user.getFailedLoginAttempts() + 1);
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.USER_LOGIN_FAILED,
                    user,
                    funnelId,
                    java.util.Map.of("reason", "bad_credentials", "attempt", user.getFailedLoginAttempts())
            );
            throw e;
        }
    }

    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(
                request.getRefreshToken()
        );

        User user = newRefreshToken.getUser();
        logger.info("SECURITY: Token refresh executed for user: {}", user.getEmail());

        String newAccessToken = jwtService.generateAccessToken(user);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken.getToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            String email = refreshTokenService.getEmailFromToken(refreshToken);
            if (email != null) {
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

    public com.joinlivora.backend.payment.dto.SubscriptionResponse getSubscriptionForUser(User user) {
        return subscriptionService.getSubscriptionForUser(user);
    }
}

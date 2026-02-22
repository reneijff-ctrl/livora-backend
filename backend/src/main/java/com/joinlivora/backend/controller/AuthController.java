package com.joinlivora.backend.controller;

import com.joinlivora.backend.auth.dto.LoginApiResponse;
import com.joinlivora.backend.auth.dto.LoginRequest;
import com.joinlivora.backend.auth.dto.LoginResponse;
import com.joinlivora.backend.auth.dto.RefreshTokenRequest;
import com.joinlivora.backend.auth.dto.TokenRefreshResponse;
import com.joinlivora.backend.auth.dto.UserDto;
import com.joinlivora.backend.auth.dto.UserMeResponse;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.security.CookieUtil;
import com.joinlivora.backend.auth.AuthService;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final com.joinlivora.backend.token.TokenWalletService tokenWalletService;
    private final com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;
    private final com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;
    private final CreatorRepository creatorRepository;

    public AuthController(AuthService authService, CookieUtil cookieUtil, 
                          com.joinlivora.backend.token.TokenWalletService tokenWalletService,
                          com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService,
                          com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService,
                          CreatorRepository creatorRepository) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.tokenWalletService = tokenWalletService;
        this.creatorProfileService = creatorProfileService;
        this.creatorPresenceService = creatorPresenceService;
        this.creatorRepository = creatorRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginApiResponse> login(
            @RequestBody LoginRequest request,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(request, funnelId, httpRequest);

        // Refresh Token Cookie (ONLY Refresh Token goes to Cookie)
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(loginResponse.getRefreshToken(), 7 * 24 * 60 * 60);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // Access Token goes into JSON Body (Frontend stores in memory)
        UserDto userDto = new UserDto(
                loginResponse.getUserId(),
                loginResponse.getEmail(),
                loginResponse.getRole()
        );

        return ResponseEntity.ok(new LoginApiResponse(
                loginResponse.getAccessToken(),
                loginResponse.getRefreshToken(),
                userDto
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody RefreshTokenRequest request,
            HttpServletResponse response
    ) {
        if (request == null || request.getRefreshToken() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh token missing"));
        }

        TokenRefreshResponse refreshResponse = authService.refresh(request);

        // New Refresh Token Cookie (still providing rotation, but token is also in body)
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(refreshResponse.getRefreshToken(), 7 * 24 * 60 * 60);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // Return full response (accessToken + optionally refreshToken if rotation is needed)
        return ResponseEntity.ok(Map.of(
                "accessToken", refreshResponse.getAccessToken(),
                "refreshToken", refreshResponse.getRefreshToken(),
                "message", "Token refreshed"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, httpRequest);

        // Clear Refresh Token Cookie
        ResponseCookie refreshCookie = cookieUtil.deleteCookie("refreshToken", "/api/auth/refresh", "None");
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody com.joinlivora.backend.auth.dto.RegisterRequest request,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String ip = RequestUtil.getClientIP(httpRequest);
        String country = RequestUtil.getClientCountry(httpRequest);
        authService.register(request, funnelId, ip, country);
        return ResponseEntity.ok(Map.of("message", "User registered successfully. Please check your email for verification."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        authService.resendVerification(principal.getName());
        return ResponseEntity.ok(Map.of("message", "Verification email resent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email != null) {
            authService.requestPasswordReset(email);
        }
        return ResponseEntity.ok(Map.of("message", "If an account with that email exists, a reset link has been sent."));
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getCurrentUser(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        com.joinlivora.backend.user.User user = authService.getUserByEmail(principal.getName());
        com.joinlivora.backend.payment.dto.SubscriptionResponse sub = authService.getSubscriptionForUser(user);
        long tokenBalance = tokenWalletService.getAvailableBalance(user.getId());
        
        com.joinlivora.backend.creator.dto.CreatorProfileDTO creatorProfile = null;
        if (user.getRole() == com.joinlivora.backend.user.Role.CREATOR) {
            creatorRepository.findByUser_Id(user.getId()).ifPresent(c -> 
                creatorPresenceService.refreshLastSeen(c.getId()));
            creatorProfile = creatorProfileService.getProfileDTO(user).orElse(null);
        }

        return ResponseEntity.ok(new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                tokenBalance,
                sub,
                creatorProfile
        ));
    }
}

package com.joinlivora.backend.controller;

import com.joinlivora.backend.auth.dto.LoginRequest;
import com.joinlivora.backend.auth.dto.LoginResponse;
import com.joinlivora.backend.auth.dto.RefreshTokenRequest;
import com.joinlivora.backend.auth.dto.TokenRefreshResponse;
import com.joinlivora.backend.security.CookieUtil;
import com.joinlivora.backend.auth.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    public AuthController(AuthService authService, CookieUtil cookieUtil) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(request, funnelId);

        // Refresh Token Cookie (ONLY Refresh Token goes to Cookie)
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(loginResponse.getRefreshToken(), 7 * 24 * 60 * 60);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // Access Token goes into JSON Body (Frontend stores in memory)
        return ResponseEntity.ok(Map.of(
                "accessToken", loginResponse.getAccessToken(),
                "message", "Login successful"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh token missing"));
        }

        TokenRefreshResponse refreshResponse = authService.refresh(new RefreshTokenRequest(refreshToken));

        // New Refresh Token Cookie
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(refreshResponse.getRefreshToken(), 7 * 24 * 60 * 60);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // New Access Token goes into JSON Body
        return ResponseEntity.ok(Map.of(
                "accessToken", refreshResponse.getAccessToken(),
                "message", "Token refreshed"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);

        // Clear Refresh Token Cookie
        ResponseCookie refreshCookie = cookieUtil.deleteCookie("refreshToken", "/auth/refresh", "None");
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody com.joinlivora.backend.auth.dto.RegisterRequest request,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId
    ) {
        authService.register(request, funnelId);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        com.joinlivora.backend.user.User user = authService.getUserByEmail(principal.getName());
        com.joinlivora.backend.payment.dto.SubscriptionResponse sub = authService.getSubscriptionForUser(user);
        
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "subscription", Map.of(
                        "status", sub != null ? sub.getStatus() : "NONE",
                        "renewalDate", sub != null && sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd() : "",
                        "cancelAtPeriodEnd", sub != null && sub.isCancelAtPeriodEnd(),
                        "nextInvoiceDate", sub != null && sub.getNextInvoiceDate() != null ? sub.getNextInvoiceDate() : "",
                        "paymentMethodBrand", sub != null && sub.getPaymentMethodBrand() != null ? sub.getPaymentMethodBrand() : "",
                        "last4", sub != null && sub.getLast4() != null ? sub.getLast4() : ""
                )
        ));
    }
}

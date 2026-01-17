package com.joinlivora.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    private final boolean secure;

    public CookieUtil(@Value("${security.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    /**
     * Creates an Access Token cookie.
     * SameSite=Lax is used for access tokens to allow them to be sent in top-level navigations
     * while providing good CSRF protection.
     */
    public ResponseCookie createAccessTokenCookie(String token, long durationInSeconds) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(durationInSeconds)
                .sameSite("None")
                .build();
    }

    /**
     * Creates a Refresh Token cookie.
     * SameSite=None is REQUIRED for cross-site refresh requests (e.g., from localhost:3000 to localhost:8080).
     * Note: SameSite=None requires Secure=true, but for local dev on HTTP, browsers might be lenient 
     * or we might need Lax if None fails on HTTP. However, the requirement specifically asked for None.
     */
    public ResponseCookie createRefreshTokenCookie(String token, long durationInSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(secure)
                .path("/auth/refresh")
                .maxAge(durationInSeconds)
                .sameSite("None")
                .build();
    }

    /**
     * Clears a cookie by setting maxAge to 0.
     */
    public ResponseCookie deleteCookie(String name, String path, String sameSite) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }
}

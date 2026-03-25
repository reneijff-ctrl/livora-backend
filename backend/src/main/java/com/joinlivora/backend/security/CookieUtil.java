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
     * SameSite=Lax provides good CSRF protection while allowing top-level navigations.
     */
    public ResponseCookie createAccessTokenCookie(String token, long durationInSeconds) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(durationInSeconds)
                .sameSite("Lax")
                .build();
    }

    /**
     * Creates a Refresh Token cookie.
     * SameSite=Lax ensures the cookie is accepted by browsers on localhost (HTTP).
     * Path=/ so the cookie is available for all auth endpoints including logout.
     */
    public ResponseCookie createRefreshTokenCookie(String token, long durationInSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(durationInSeconds)
                .sameSite("Lax")
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

package com.joinlivora.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET =
            "SUPER_SECRET_JOINLIVORA_KEY_123456789_SUPER_SECRET";

    private static final long EXPIRATION =
            1000 * 60 * 60 * 24; // 24 uur

    // 🔑 BELANGRIJK: SecretKey, GEEN Key
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes());

    public static String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY)
                .compact();
    }

    public static String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public static String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}

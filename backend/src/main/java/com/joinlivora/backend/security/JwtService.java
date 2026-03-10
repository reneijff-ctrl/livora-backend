package com.joinlivora.backend.security;

import com.joinlivora.backend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long jwtExpiration;
    private final long refreshExpiration;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration}") long jwtExpiration,
            @Value("${security.jwt.refresh-token-expiration}") long refreshExpiration,
            @Value("${security.jwt.issuer:joinlivora-backend}") String issuer,
            @Value("${security.jwt.audience:joinlivora-frontend}") String audience
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
        this.issuer = issuer;
        this.audience = audience;
    }

    // ======================
    // TOKEN GENERATION
    // ======================

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration * 1000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration * 1000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private String buildToken(User user, long expiration, boolean includeRole) {
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(secretKey, Jwts.SIG.HS256);

        if (includeRole) {
            builder.claim("role", user.getRole().name());
        }

        return builder.compact();
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration * 1000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // ======================
    // TOKEN VALIDATION
    // ======================

    /**
     * Validates the token and returns the claims.
     * Throws JwtException if the token is invalid, expired, or malformed.
     */
    public Claims validateToken(String token) {
        var claimsJws = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);
        
        // Explicitly check algorithm to prevent algorithm switching attacks
        String alg = claimsJws.getHeader().getAlgorithm();
        if (!"HS256".equals(alg)) {
            throw new io.jsonwebtoken.security.SignatureException("Invalid algorithm: " + alg);
        }
        
        return claimsJws.getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = validateToken(token);
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    // ======================
    // CLAIM EXTRACTION
    // ======================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractUsername(token);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = validateToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return validateToken(token);
    }

    private Claims getClaims(String token) {
        return validateToken(token);
    }
}

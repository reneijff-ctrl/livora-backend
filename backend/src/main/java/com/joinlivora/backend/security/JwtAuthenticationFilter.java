package com.joinlivora.backend.security;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.exception.ErrorResponse;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRiskStateRepository userRiskStateRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRiskStateRepository userRiskStateRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService
    ) {
        this.jwtService = jwtService;
        this.userRiskStateRepository = userRiskStateRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/content/public");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            final Claims claims = jwtService.validateToken(jwt);
            userEmail = claims.getSubject();

            // Silent check for soft block
            if (abuseDetectionService != null && abuseDetectionService.isSoftBlocked(null, request.getRemoteAddr())) {
                log.info("SILENT_DETECTION: IP {} is soft-blocked in AbuseDetectionService", request.getRemoteAddr());
            }

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Check if creator is blocked
                Optional<com.joinlivora.backend.user.User> userEntityOpt = userRepository.findByEmail(userEmail);
                if (userEntityOpt.isPresent()) {
                    com.joinlivora.backend.user.User userEntity = userEntityOpt.get();
                    Long userId = userEntity.getId();
                    
                    // Silent check for soft block for creator
                    if (abuseDetectionService != null && abuseDetectionService.isSoftBlocked(new UUID(0L, userId), request.getRemoteAddr())) {
                        log.info("SILENT_DETECTION: User {} is soft-blocked in AbuseDetectionService", userId);
                    }

                    var riskState = userRiskStateRepository.findById(userId);
                    if (riskState.isPresent() && riskState.get().getBlockedUntil() != null && riskState.get().getBlockedUntil().isAfter(Instant.now())) {
                        log.warn("SECURITY [fraud_protection]: Request rejected for blocked creator: {}", userEmail);
                        
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpServletResponse.SC_FORBIDDEN)
                                .error("Forbidden")
                                .message("Your account is temporarily blocked due to suspicious activity.")
                                .path(request.getRequestURI())
                                .errorCode("USER_TEMP_BLOCKED")
                                .build();
                                
                        sendErrorResponse(response, errorResponse);
                        return;
                    }

                    if (userEntity.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED || 
                        userEntity.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED) {
                        log.warn("SECURITY: Request rejected for SUSPENDED/TERMINATED creator: {}", userEmail);
                        
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpServletResponse.SC_FORBIDDEN)
                                .error("Forbidden")
                                .message("Your account is restricted.")
                                .path(request.getRequestURI())
                                .errorCode(userEntity.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED ? "USER_TERMINATED" : "USER_SUSPENDED")
                                .build();
                                
                        sendErrorResponse(response, errorResponse);
                        return;
                    }

                    // Check if sessions were invalidated
                    Instant iat = claims.getIssuedAt().toInstant();
                    if (userEntity.getSessionsInvalidatedAt() != null && iat.isBefore(userEntity.getSessionsInvalidatedAt())) {
                        log.warn("SECURITY: Request rejected for invalidated session of user: {}", userEmail);
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpServletResponse.SC_UNAUTHORIZED)
                                .error("Unauthorized")
                                .message("Session has been invalidated. Please log in again.")
                                .path(request.getRequestURI())
                                .errorCode("SESSION_INVALIDATED")
                                .build();
                        sendErrorResponse(response, errorResponse);
                        return;
                    }

                    // Check for active TEMP_SUSPENSION restriction
                    var activeRestriction = restrictionService.getActiveRestriction(new UUID(0L, userId));
                    if (activeRestriction.isPresent() && activeRestriction.get().getRestrictionLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION) {
                        log.warn("SECURITY: Request rejected for TEMP_SUSPENDED creator: {}", userEmail);
                        
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpServletResponse.SC_FORBIDDEN)
                                .error("Forbidden")
                                .message("Your account is temporarily suspended.")
                                .path(request.getRequestURI())
                                .errorCode("USER_RESTRICTED_TEMP_SUSPENSION")
                                .expiresAt(activeRestriction.get().getExpiresAt())
                                .build();
                                
                        sendErrorResponse(response, errorResponse);
                        return;
                    }
                }

                String role = claims.get("role", String.class);
                
                UserDetails userDetails;
                if (userEntityOpt.isPresent()) {
                    userDetails = new UserPrincipal(userEntityOpt.get());
                } else {
                    java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                    if (role != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        if (!"USER".equals(role)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        }
                    }
                    userDetails = new User(
                        userEmail,
                        "", // Password not needed in context
                        authorities
                    );
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
                MDC.put("creator", userEmail);
                log.info("SECURITY: User authenticated: {} with roles: {}", userEmail, userDetails.getAuthorities());
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("SECURITY: JWT token expired from IP: {}. Error: {}", request.getRemoteAddr(), e.getMessage());
            sendErrorResponse(response, ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpServletResponse.SC_UNAUTHORIZED)
                    .error("Unauthorized")
                    .message("JWT token has expired")
                    .path(request.getRequestURI())
                    .errorCode("TOKEN_EXPIRED")
                    .build());
            return;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            log.warn("SECURITY: Invalid JWT token from IP: {}. Error: {}", request.getRemoteAddr(), e.getMessage());
            abuseDetectionService.trackEvent(null, request.getRemoteAddr(), AbuseEventType.SUSPICIOUS_API_USAGE, "Invalid JWT token: " + e.getMessage());
            sendErrorResponse(response, ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpServletResponse.SC_UNAUTHORIZED)
                    .error("Unauthorized")
                    .message("Invalid or malformed JWT token")
                    .path(request.getRequestURI())
                    .errorCode("TOKEN_INVALID")
                    .build());
            return;
        } catch (Exception e) {
            log.error("SECURITY: Authentication internal error from IP: {}. Error: {}", request.getRemoteAddr(), e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, ErrorResponse errorResponse) throws IOException {
        response.setStatus(errorResponse.getStatus());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

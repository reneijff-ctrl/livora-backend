package com.joinlivora.backend.security;

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
import java.util.Collections;

@Component
@lombok.extern.slf4j.Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
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
            userEmail = jwtService.extractEmail(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Optimization: Extract roles from JWT claims to avoid DB hit per request
                if (jwtService.isTokenValid(jwt)) {
                    String role = jwtService.extractRole(jwt);
                    
                    UserDetails userDetails = new User(
                            userEmail,
                            "", // Password not needed in context
                            role != null ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) : Collections.emptyList()
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    MDC.put("userId", userEmail);
                    log.debug("SECURITY: User authenticated: {}", userEmail);
                }
            }
        } catch (Exception e) {
            // In case of any error (expired token, etc.), we let the request continue.
            // SecurityConfig will handle unauthorized access to protected endpoints.
            log.warn("SECURITY: Authentication failed for request from IP: {}. Error: {}", request.getRemoteAddr(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

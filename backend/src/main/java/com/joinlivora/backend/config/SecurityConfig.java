package com.joinlivora.backend.config;

import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${livora.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @org.springframework.beans.factory.annotation.Value("${livora.security.enforce-https:false}")
    private boolean enforceHttps;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, 
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            FunnelTrackingFilter funnelTrackingFilter,
            AuditLogoutHandler auditLogoutHandler,
            ObjectMapper objectMapper,
            Environment env
    ) throws Exception {
        // CSRF Handler for stateless token handling
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        // CSRF Repository with hardening
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> {
            cookie.sameSite("None"); // Required for cross-site cookies if frontend and backend are on different domains
            cookie.secure(enforceHttps);
            cookie.path("/");
        });

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(funnelTrackingFilter, RateLimitingFilter.class)
            .csrf(csrf -> {
                if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
                    csrf.disable();
                } else {
                    csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/", "/login", "/register", "/pricing", "/explore", "/api/auth/**", "/api/public/**", "/api/creators/**", "/webhooks/stripe", "/api/stripe/webhook", "/api/stream/auth", "/api/stream/auth-done", "/api/stream/record-done", "/ws/**", "/api/health", "/actuator/health", "/api/actuator/health", "/api/creator/**", "/api/payments/**");
                }
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .headers(headers -> {
                headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                    .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://stripe.com; connect-src 'self' https://api.stripe.com")
                    )
                    .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicy(permissions -> permissions.policy("camera=self; microphone=self; geolocation=self; payment=self"));
                
                if (enforceHttps) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000)
                    );
                } else {
                    headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                }
            })
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
                    String maskedIp = request.getRemoteAddr().replaceAll("(\\d+)\\.(\\d+)\\..*", "$1.$2.***.***");
                    log.warn("SECURITY: Unauthorized access attempt to {} from IP: {}. Error: {}", 
                            request.getRequestURI(), maskedIp, authException.getMessage());
                    
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .timestamp(Instant.now())
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .error("Unauthorized")
                            .message(authException.getMessage())
                            .path(request.getRequestURI())
                            .requestId(MDC.get("requestId"))
                            .errorCode("UNAUTHORIZED")
                            .build();
                    
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
                    String maskedIp = request.getRemoteAddr().replaceAll("(\\d+)\\.(\\d+)\\..*", "$1.$2.***.***");
                    org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    log.warn("SECURITY: Access denied for {} from IP: {}. User: {}. Roles: {}. Error: {}", 
                            request.getRequestURI(), maskedIp, (auth != null ? auth.getName() : "anonymous"), 
                            (auth != null ? auth.getAuthorities() : "none"), accessDeniedException.getMessage());
                    
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .timestamp(Instant.now())
                            .status(HttpStatus.FORBIDDEN.value())
                            .error("Forbidden")
                            .message("You do not have permission to access this resource")
                            .path(request.getRequestURI())
                            .requestId(MDC.get("requestId"))
                            .errorCode("FORBIDDEN")
                            .build();
                    
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/actuator/health").permitAll()
                .requestMatchers("/api/creators/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/webhooks/stripe", "/api/stripe/webhook").permitAll()
                .requestMatchers("/", "/login", "/register", "/pricing", "/explore", "/ws-test.html", "/api/health", "/ws/**", "/uploads/**", "/thumbnails/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/creators/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/creators/me/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/creators/*/follow/status").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/creators/**", "/api/posts/public", "/api/posts/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/creators/*/follow").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/creators/*/follow").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/leaderboards").permitAll()
                .requestMatchers("/api/creator/tip-goals/public/**").permitAll()
                .requestMatchers("/api/creator/onboarding/**").authenticated()
                .requestMatchers("/api/creator/stripe/**").hasRole("CREATOR")
                .requestMatchers("/api/creator/**").hasAnyRole("CREATOR", "ADMIN")
                .requestMatchers("/api/dashboard/creator/**").hasAnyRole("CREATOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payments/create-checkout-session").hasAnyRole("CREATOR", "USER")
                .requestMatchers("/api/admin/fraud/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/internal/**").hasRole("ADMIN")
                .requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                .requestMatchers("/api/premium/**").hasRole("PREMIUM")
                .requestMatchers("/api/user/me").authenticated()
                .requestMatchers("/api/user/**").hasRole("USER")
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/stream/auth", "/api/stream/auth-done", "/api/stream/record-done").permitAll()
                .requestMatchers("/api/stream/live", "/api/stream/vod", "/api/stream/{creatorId}", "/api/stream/room/{id}", "/api/stream/{id}/status", "/api/stream/{id}/pinned", "/api/stream/creator/{creatorId}/pinned", "/api/stream/{id}/highlight").authenticated()
                .requestMatchers("/api/stream/{id}/hls").authenticated()
                .requestMatchers("/api/hls/**").authenticated()
                .requestMatchers("/api/stream/**").hasAnyRole("CREATOR", "ADMIN")
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN", "Accept", "X-Requested-With", "Cache-Control", "Origin"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour preflight cache

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

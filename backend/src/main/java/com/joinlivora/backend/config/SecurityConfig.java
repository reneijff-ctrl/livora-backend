package com.joinlivora.backend.config;

import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, 
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter
    ) throws Exception {
        // CSRF Handler for stateless token handling
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        // CSRF Repository with hardening
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> {
            cookie.sameSite("None"); // Required for cross-site cookies if frontend and backend are on different domains
            cookie.secure(true);
            cookie.path("/");
        });

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterBefore(rateLimitingFilter, LogoutFilter.class)
            .addFilterBefore(funnelTrackingFilter, RateLimitingFilter.class)
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/auth/login", "/auth/register", "/api/payments/webhook/**")
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000)
                )
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://stripe.com; connect-src 'self' https://api.stripe.com")
                )
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
                    log.warn("SECURITY: Unauthorized access attempt to {} from IP: {}. Error: {}", 
                            request.getRequestURI(), request.getRemoteAddr(), authException.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Unauthorized: " + authException.getMessage());
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
                    log.warn("SECURITY: Access denied for {} from IP: {}. Error: {}", 
                            request.getRequestURI(), request.getRemoteAddr(), accessDeniedException.getMessage());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("Forbidden: " + accessDeniedException.getMessage());
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/auth/refresh", "/auth/register").permitAll()
                .requestMatchers("/api/payments/webhook/**").permitAll()
                .requestMatchers("/api/flags/**").permitAll()
                .requestMatchers("/api/creator/**").hasAnyRole("CREATOR", "ADMIN")
                .requestMatchers("/api/content/public").permitAll()
                .requestMatchers("/api/content/**").authenticated()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus", "/actuator/metrics").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/subscriptions/me").authenticated()
                .requestMatchers("/api/user/**").hasRole("USER")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // In production, FRONTEND_URL should be set to the actual domain
        String frontendUrl = System.getenv("FRONTEND_URL");
        if (frontendUrl != null && !frontendUrl.isEmpty()) {
            configuration.setAllowedOrigins(List.of(frontendUrl));
        } else {
            configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://joinlivora.com"));
        }
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

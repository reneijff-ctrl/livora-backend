package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.auth.AuthService;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.creator.dto.CreatorProfileDTO;
import com.joinlivora.backend.creator.service.CreatorApplicationService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorOnboardingController.class)
@Import(SecurityConfig.class)
class CreatorOnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthService authService;

    @MockBean
    private TokenWalletService tokenWalletService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private CreatorApplicationService creatorApplicationService;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(funnelTrackingFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void startApplication_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/creator/onboarding/start")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void submitApplication_ShouldReturnOk() throws Exception {
        String body = "{\"termsAccepted\": true, \"ageVerified\": true}";
        mockMvc.perform(post("/api/creator/onboarding/submit")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getStatus_ShouldReturnOk() throws Exception {
        when(creatorApplicationService.getApplication(any())).thenReturn(java.util.Optional.of(new com.joinlivora.backend.creator.model.CreatorApplication()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/creator/onboarding/status"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void upgradeAndAuth_Authenticated_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/creator/onboarding/upgrade-and-auth")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void upgradeAndAuth_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/creator/onboarding/upgrade-and-auth")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}









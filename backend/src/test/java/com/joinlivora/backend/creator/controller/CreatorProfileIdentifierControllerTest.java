package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorIdentifierDTO;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.LoginFailureHandler;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.UserRepository;

@WebMvcTest(CreatorProfileController.class)
@Import(SecurityConfig.class)
class CreatorProfileIdentifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private AuditService auditService;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private LoginFailureHandler loginFailureHandler;

    @MockBean
    private UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws jakarta.servlet.ServletException, java.io.IOException {
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
    void getMyIdentifier_ShouldReturnIdentifier() throws Exception {
        CreatorIdentifierDTO dto = new CreatorIdentifierDTO("test-slug");
        UserPrincipal principal = new UserPrincipal(1L, "creator@test.com", "password", 
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR")));

        when(creatorProfileService.getPublicIdentifier(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/creator/profile/me/identifier")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("test-slug"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getMyIdentifier_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/profile/me/identifier")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}









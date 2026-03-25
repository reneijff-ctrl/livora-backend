package com.joinlivora.backend.controller;

import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payout.CreatorDashboardService;
import com.joinlivora.backend.payout.dto.CreatorDashboardDto;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorDashboardController.class)
@Import(SecurityConfig.class)
class CreatorDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private CreatorDashboardService creatorDashboardService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
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
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void getCreatorDashboard_AsCreator_ShouldReturnData() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        user.setRole(Role.CREATOR);
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        CreatorDashboardDto dashboard = CreatorDashboardDto.builder()
                .totalEarnings(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("50.00"))
                .activeStreams(1)
                .totalSubscribers(10)
                .contentCount(5)
                .build();
        when(creatorDashboardService.getDashboard(user)).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard/creator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnings").value(100.00))
                .andExpect(jsonPath("$.availableBalance").value(50.00))
                .andExpect(jsonPath("$.activeStreams").value(1))
                .andExpect(jsonPath("$.totalSubscribers").value(10))
                .andExpect(jsonPath("$.contentCount").value(5));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getCreatorDashboard_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/creator"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCreatorDashboard_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/creator"))
                .andExpect(status().isUnauthorized());
    }
}









package com.joinlivora.backend.controller;

import com.joinlivora.backend.payout.CreatorBalanceService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payout.dto.CreatorEarningsSummary;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;

@WebMvcTest(CreatorEarningsController.class)
@Import(SecurityConfig.class)
class CreatorEarningsSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorEarningsService creatorEarningsService;

    @MockBean
    private CreatorBalanceService creatorBalanceService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @BeforeEach
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
    @WithMockUser(roles = "CREATOR")
    void getEarningsSummary_ShouldReturnSummary() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");

        CreatorEarningsSummary dto = CreatorEarningsSummary.builder()
                .totalEarned(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("30.00"))
                .pendingBalance(new BigDecimal("70.00"))
                .monthEarnings(new BigDecimal("50.00"))
                .lastPayoutDate(null)
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getEarningsSummary(user)).thenReturn(dto);

        mockMvc.perform(get("/api/creator/earnings/summary")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarned").value(100.00))
                .andExpect(jsonPath("$.availableBalance").value(30.00))
                .andExpect(jsonPath("$.pendingBalance").value(70.00))
                .andExpect(jsonPath("$.monthEarnings").value(50.00))
                .andExpect(jsonPath("$.lastPayoutDate").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEarningsSummary_AsAdmin_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/earnings/summary")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEarningsSummary_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/earnings/summary")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEarningsSummary_Unauthorized_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/creator/earnings/summary")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}









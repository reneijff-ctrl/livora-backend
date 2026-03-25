package com.joinlivora.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.payout.CreatorBalanceService;
import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payout.dto.CreatorEarningDto;
import com.joinlivora.backend.payout.dto.CreatorEarningsDTO;
import com.joinlivora.backend.payout.dto.CreatorEarningsOverviewDTO;
import com.joinlivora.backend.payout.dto.CreatorEarningsReportDTO;
import com.joinlivora.backend.payout.dto.CreatorEarningsResponseDTO;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;

@WebMvcTest(CreatorEarningsController.class)
@Import(SecurityConfig.class)
class CreatorEarningsControllerTest {

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
    void getEarnings_ShouldReturnEarnings() throws Exception {
        User user = new User();
        user.setEmail("user");
        
        CreatorEarningsOverviewDTO dto = CreatorEarningsOverviewDTO.builder()
                .totalEarnings(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("30.00"))
                .pendingBalance(new BigDecimal("70.00"))
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getEarningsOverview(any())).thenReturn(dto);

        mockMvc.perform(get("/api/creator/earnings")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnings").value(100.00))
                .andExpect(jsonPath("$.availableBalance").value(30.00))
                .andExpect(jsonPath("$.pendingBalance").value(70.00));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getEarningsReport_ShouldReturnReport() throws Exception {
        User user = new User();
        user.setEmail("user");

        CreatorEarningsReportDTO report = CreatorEarningsReportDTO.builder()
                .daily(CreatorEarningsReportDTO.PeriodStats.builder()
                        .totalEarnings(new BigDecimal("100.00"))
                        .totalTokens(5000L)
                        .build())
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getEarningsReport(any())).thenReturn(report);

        mockMvc.perform(get("/api/creator/earnings/report")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.totalEarnings").value(100.00))
                .andExpect(jsonPath("$.daily.totalTokens").value(5000));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getRecentTransactions_ShouldReturnTransactions() throws Exception {
        User user = new User();
        user.setEmail("user");

        CreatorEarningDto dto = CreatorEarningDto.builder()
                .grossAmount(new BigDecimal("10.00"))
                .netAmount(new BigDecimal("8.00"))
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getRecentTransactions(any(), anyInt())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/creator/earnings/transactions")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].grossAmount").value(10.00))
                .andExpect(jsonPath("$[0].netAmount").value(8.00));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getBalance_ShouldReturnBalance() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        
        java.util.UUID balanceId = java.util.UUID.randomUUID();
        CreatorEarningsResponseDTO dto = CreatorEarningsResponseDTO.builder()
                .id(balanceId)
                .availableBalance(new BigDecimal("50.00"))
                .pendingBalance(new BigDecimal("20.00"))
                .totalEarned(new BigDecimal("70.00"))
                .payoutsDisabled(false)
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getBalance(user)).thenReturn(java.util.Optional.of(dto));

        mockMvc.perform(get("/api/creator/earnings/balance")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(balanceId.toString()))
                .andExpect(jsonPath("$.availableBalance").value(50.00))
                .andExpect(jsonPath("$.pendingBalance").value(20.00))
                .andExpect(jsonPath("$.totalEarned").value(70.00))
                .andExpect(jsonPath("$.payoutsDisabled").value(false));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getBalance_WhenNotFound_ShouldReturn404() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");

        when(userService.getByEmail(any())).thenReturn(user);
        when(creatorEarningsService.getBalance(user)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/creator/earnings/balance")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBalance_AsAdmin_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/earnings/balance")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}









package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.repository.VelocityMetricRepository;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminVelocityController.class)
@Import(SecurityConfig.class)
class AdminVelocityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private VelocityMetricRepository velocityMetricRepository;

    @MockBean
    private RuleFraudSignalRepository fraudSignalRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

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
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

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
    @WithMockUser(roles = "ADMIN")
    void getUserVelocity_AsAdmin_ShouldReturnData() throws Exception {
        Long userId = 123L;
        UUID userUuid = new UUID(0L, userId);
        
        UserRiskState riskState = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.MEDIUM)
                .paymentLocked(true)
                .build();
        
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.of(riskState));
        when(velocityMetricRepository.findAllByUserIdAndWindowEndAfter(eq(userId), any())).thenReturn(Collections.emptyList());
        when(fraudSignalRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/velocity/" + userUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.intValue()))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.paymentLocked").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUserVelocity_AsUser_ShouldReturnForbidden() throws Exception {
        UUID userUuid = UUID.randomUUID();
        mockMvc.perform(get("/api/admin/velocity/" + userUuid))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserVelocity_Anonymous_ShouldReturnUnauthorized() throws Exception {
        UUID userUuid = UUID.randomUUID();
        mockMvc.perform(get("/api/admin/velocity/" + userUuid))
                .andExpect(status().isUnauthorized());
    }
}









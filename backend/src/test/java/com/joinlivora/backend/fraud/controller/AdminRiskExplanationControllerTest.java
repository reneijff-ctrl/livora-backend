package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskExplanationAuditService;
import com.joinlivora.backend.fraud.service.RiskExplanationService;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRiskExplanationController.class)
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class AdminRiskExplanationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskExplanationService riskExplanationService;

    @MockBean
    private RiskExplanationAuditService riskExplanationAuditService;

    @MockBean
    private UserService userService;

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

    private User admin;

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

        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setRole(Role.ADMIN);
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getExplanationsForSubject_AsAdmin_ShouldReturnExplanationsAndLog() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID explanationId = UUID.randomUUID();
        RiskExplanation explanation = RiskExplanation.builder()
                .id(explanationId)
                .subjectType(RiskSubjectType.USER)
                .subjectId(subjectId)
                .riskScore(50)
                .decision(RiskDecision.REVIEW)
                .explanationText("Test explanation")
                .build();

        when(riskExplanationService.getExplanationsForSubject(subjectId, RiskSubjectType.USER))
                .thenReturn(List.of(explanation));

        mockMvc.perform(get("/api/admin/risk/explanations/USER/" + subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].explanationText").value("Test explanation"))
                .andExpect(jsonPath("$[0].riskScore").value(50));

        verify(riskExplanationAuditService).logRequest(eq(new UUID(0L, 1L)), eq(Role.ADMIN), eq(explanationId));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getExplanationById_AsAdmin_ShouldReturnExplanationAndLog() throws Exception {
        UUID explanationId = UUID.randomUUID();
        RiskExplanation explanation = RiskExplanation.builder()
                .id(explanationId)
                .explanationText("Detailed explanation")
                .build();

        when(riskExplanationService.getExplanationById(explanationId))
                .thenReturn(Optional.of(explanation));

        mockMvc.perform(get("/api/admin/risk/explanation/" + explanationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanationText").value("Detailed explanation"));

        verify(riskExplanationAuditService).logRequest(eq(new UUID(0L, 1L)), eq(Role.ADMIN), eq(explanationId));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getExplanationsForSubject_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/risk/explanations/USER/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getExplanationById_NotFound_ShouldReturn404() throws Exception {
        UUID explanationId = UUID.randomUUID();
        when(riskExplanationService.getExplanationById(explanationId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/risk/explanation/" + explanationId))
                .andExpect(status().isNotFound());
    }
}









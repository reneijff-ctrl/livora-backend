package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.config.SecurityConfig;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreatorAdaptivePerformanceController.class)
@Import(SecurityConfig.class)
class CreatorAdaptivePerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorAdaptivePerformanceService service;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

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
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getPerformance_AsCreator_ShouldReturnData() throws Exception {
        User creator = new User();
        creator.setEmail("creator@example.com");

        CreatorAdaptivePerformanceDTO dto = CreatorAdaptivePerformanceDTO.builder()
                .experimentsRun(5)
                .successRate(0.8)
                .averageRevenueLift(15.2)
                .averageRiskReduction(0.1)
                .momentum("POSITIVE")
                .engineConfidence(0.7)
                .build();

        when(userService.getByEmail(anyString())).thenReturn(creator);
        when(service.getCreatorMetrics(creator)).thenReturn(dto);

        mockMvc.perform(get("/api/creator/analytics/adaptive-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experimentsRun").exists())
                .andExpect(jsonPath("$.successRate").exists())
                .andExpect(jsonPath("$.averageRevenueLift").exists())
                .andExpect(jsonPath("$.averageRiskReduction").exists())
                .andExpect(jsonPath("$.momentum").exists())
                .andExpect(jsonPath("$.engineConfidence").exists());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getPerformance_AsAdmin_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/analytics/adaptive-performance"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPerformance_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/creator/analytics/adaptive-performance"))
                .andExpect(status().isUnauthorized());
    }
}









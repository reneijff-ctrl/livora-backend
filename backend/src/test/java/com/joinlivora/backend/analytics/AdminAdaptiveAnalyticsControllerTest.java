package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.AdaptiveEngineMetricsDTO;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAdaptiveAnalyticsController.class)
@Import(SecurityConfig.class)
class AdminAdaptiveAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdaptiveEngineAnalyticsService adaptiveEngineAnalyticsService;

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
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getMetrics_AsAdmin_ShouldReturnData() throws Exception {
        AdaptiveEngineMetricsDTO dto = AdaptiveEngineMetricsDTO.builder()
                .totalExperiments(10)
                .successfulExperiments(7)
                .successRate(0.7)
                .averageRevenueLift(25.5)
                .averageRiskDelta(0.15)
                .activeExperiments(3)
                .build();

        when(adaptiveEngineAnalyticsService.getGlobalAdaptiveMetrics()).thenReturn(dto);

        mockMvc.perform(get("/api/admin/analytics/adaptive-engine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExperiments").value(10))
                .andExpect(jsonPath("$.successfulExperiments").value(7))
                .andExpect(jsonPath("$.successRate").value(0.7))
                .andExpect(jsonPath("$.averageRevenueLift").value(25.5))
                .andExpect(jsonPath("$.averageRiskDelta").value(0.15))
                .andExpect(jsonPath("$.activeExperiments").value(3));
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getMetrics_AsCreator_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/adaptive-engine"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMetrics_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/adaptive-engine"))
                .andExpect(status().isUnauthorized());
    }
}









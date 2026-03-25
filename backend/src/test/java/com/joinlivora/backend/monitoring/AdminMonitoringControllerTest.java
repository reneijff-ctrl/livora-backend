package com.joinlivora.backend.monitoring;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.monitoring.dto.RevenueSummaryResponse;
import com.joinlivora.backend.monitoring.dto.SystemHealthResponse;
import com.joinlivora.backend.monitoring.dto.SystemMetricsResponse;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.security.AuditLogoutHandler;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMonitoringController.class)
@Import(SecurityConfig.class)
class AdminMonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMonitoringService monitoringService;

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
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @BeforeEach
    void setup() throws Exception {
        // Mock filters to just continue chain
        doAnswer(invocation -> {
            ((jakarta.servlet.FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            ((jakarta.servlet.FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            ((jakarta.servlet.FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(funnelTrackingFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getHealth_AsAdmin_ShouldReturnOk() throws Exception {
        when(monitoringService.getSystemHealth()).thenReturn(SystemHealthResponse.builder()
                .status("UP")
                .components(Map.of("db", "UP"))
                .build());

        mockMvc.perform(get("/api/admin/system/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMetrics_AsAdmin_ShouldReturnOk() throws Exception {
        when(monitoringService.getSystemMetrics()).thenReturn(SystemMetricsResponse.builder()
                .totalUsers(100)
                .build());

        mockMvc.perform(get("/api/admin/system/metrics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(100));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRevenueSummary_AsAdmin_ShouldReturnOk() throws Exception {
        when(monitoringService.getRevenueSummary()).thenReturn(RevenueSummaryResponse.builder()
                .totalRevenue(new BigDecimal("1000.00"))
                .build());

        mockMvc.perform(get("/api/admin/system/revenue-summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1000.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void getMediasoupStats_AsAdmin_ShouldReturnOk() throws Exception {
        com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupGlobalStats global = 
            new com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupGlobalStats(
                1, 1, 1, 1
            );
        com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupStatsResponse stats = 
            new com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupStatsResponse(
                global, java.util.Collections.emptyList()
            );
            
        when(monitoringService.getMediasoupStats()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(stats));

        org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc.perform(get("/api/admin/system/mediasoup/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global.routers").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getHealth_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/system/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}









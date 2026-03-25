package com.joinlivora.backend.payment;

import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payment.dto.TaxSummaryDTO;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaxController.class)
@Import(SecurityConfig.class)
class TaxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private TaxService taxService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

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
    @WithMockUser(roles = "ADMIN")
    void getTaxSummary_AsAdmin_ShouldReturnSummary() throws Exception {
        TaxSummaryDTO summary = TaxSummaryDTO.builder()
                .totalRevenue(new BigDecimal("1000.00"))
                .totalVat(new BigDecimal("200.00"))
                .revenueByCountry(Map.of("FR", new BigDecimal("1000.00")))
                .build();

        when(taxService.getTaxSummary(any(Instant.class), any(Instant.class))).thenReturn(summary);

        mockMvc.perform(get("/api/admin/tax/summary")
                        .param("startDate", "2026-01-01T00:00:00Z")
                        .param("endDate", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1000.00))
                .andExpect(jsonPath("$.totalVat").value(200.00))
                .andExpect(jsonPath("$.revenueByCountry.FR").value(1000.00));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTaxSummary_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/tax/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTaxSummary_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/tax/summary"))
                .andExpect(status().isUnauthorized());
    }
}









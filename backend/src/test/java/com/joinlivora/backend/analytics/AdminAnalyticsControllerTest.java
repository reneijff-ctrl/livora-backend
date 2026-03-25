package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyticsController.class)
@Import(SecurityConfig.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private AnalyticsEventRepository analyticsEventRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private UserSubscriptionRepository subscriptionRepository;

    @MockBean
    private CreatorAnalyticsService creatorAnalyticsService;

    @MockBean
    private PlatformAnalyticsRepository platformAnalyticsRepository;

    @MockBean
    private ExperimentAnalyticsRepository experimentAnalyticsRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private AnalyticsEventPublisher analyticsEventPublisher;

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
    void getCreatorAnalytics_AsAdmin_ShouldReturnData() throws Exception {
        UUID profileId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);

        CreatorAnalyticsResponseDTO dto = new CreatorAnalyticsResponseDTO(
                from, new BigDecimal("500.00"), 100, 10, 20, 450, 3.2);

        when(creatorAnalyticsService.getAnalyticsByProfileId(eq(profileId), eq(from), eq(to)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/analytics/creators/" + profileId)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].earnings").value(500.00))
                .andExpect(jsonPath("$[0].viewers").value(100))
                .andExpect(jsonPath("$[0].avgSessionDuration").value(450));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getCreatorAnalytics_AsCreator_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/creators/" + UUID.randomUUID())
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isForbidden());
    }
}









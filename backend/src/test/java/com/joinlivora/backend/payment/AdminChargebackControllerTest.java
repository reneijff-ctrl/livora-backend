package com.joinlivora.backend.payment;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminChargebackController.class)
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class AdminChargebackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.chargeback.ChargebackService chargebackService;

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

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ChargebackCase buildCase(UUID id, String reason, ChargebackStatus status) {
        ChargebackCase c = new ChargebackCase();
        c.setId(id);
        c.setUserId(UUID.randomUUID());
        c.setPaymentIntentId("pi_test_" + id);
        c.setAmount(BigDecimal.valueOf(100));
        c.setCurrency("usd");
        c.setReason(reason);
        c.setStatus(status);
        c.setFraudScoreAtTime(5);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }

    // ---------------------------------------------------------------------------
    // Tests: GET /api/admin/chargebacks
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllChargebacks_AsAdmin_ShouldReturnMappedData() throws Exception {
        UUID id = UUID.randomUUID();
        ChargebackCase c = buildCase(id, "fraud", ChargebackStatus.OPEN);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(chargebackService.getAllChargebackCasesPaged(any()))
                .thenReturn(new PageImpl<>(List.of(c), pageable, 1));

        mockMvc.perform(get("/api/admin/chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("fraud"))
                // OPEN → maps to RECEIVED
                .andExpect(jsonPath("$.content[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.content[0].resolved").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllChargebacks_WonCase_ShouldMapToWon() throws Exception {
        UUID id = UUID.randomUUID();
        ChargebackCase c = buildCase(id, "duplicate", ChargebackStatus.WON);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(chargebackService.getAllChargebackCasesPaged(any()))
                .thenReturn(new PageImpl<>(List.of(c), pageable, 1));

        mockMvc.perform(get("/api/admin/chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("WON"))
                .andExpect(jsonPath("$.content[0].resolved").value(true));
    }

    // ---------------------------------------------------------------------------
    // Tests: GET /api/admin/chargebacks/{id}
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargebackById_AsAdmin_ShouldReturnMappedData() throws Exception {
        UUID id = UUID.randomUUID();
        ChargebackCase c = buildCase(id, "fraud", ChargebackStatus.UNDER_REVIEW);
        when(chargebackService.getChargebackById(id)).thenReturn(Optional.of(c));

        mockMvc.perform(get("/api/admin/chargebacks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("fraud"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargebackById_NotFound_ShouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        when(chargebackService.getChargebackById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/chargebacks/" + id))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // Tests: GET /api/admin/chargebacks/correlated/{userId}
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCorrelatedChargebacks_AsAdmin_ShouldReturnMappedData() throws Exception {
        UUID userId = UUID.randomUUID();
        ChargebackCase c = buildCase(UUID.randomUUID(), "correlated", ChargebackStatus.OPEN);
        when(chargebackService.findCorrelatedCasesByUserId(userId.getLeastSignificantBits()))
                .thenReturn(List.of(c));

        mockMvc.perform(get("/api/admin/chargebacks/correlated/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("correlated"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"));
    }

    // ---------------------------------------------------------------------------
    // Tests: authorization
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "USER")
    void getAllChargebacks_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/chargebacks"))
                .andExpect(status().isForbidden());
    }
}

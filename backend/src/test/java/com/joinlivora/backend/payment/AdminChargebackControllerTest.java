package com.joinlivora.backend.payment;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
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
    private ChargebackRepository chargebackRepository;

    @MockBean
    private PaymentChargebackService paymentChargebackService;

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

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllChargebacks_AsAdmin_ShouldReturnData() throws Exception {
        Chargeback cb = Chargeback.builder()
                .id(UUID.randomUUID())
                .reason("fraud")
                .build();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(chargebackRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cb), pageable, 1));

        mockMvc.perform(get("/api/admin/chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("fraud"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargebackById_AsAdmin_ShouldReturnData() throws Exception {
        UUID id = UUID.randomUUID();
        Chargeback cb = Chargeback.builder()
                .id(id)
                .reason("fraud")
                .build();
        when(chargebackRepository.findById(id)).thenReturn(Optional.of(cb));

        mockMvc.perform(get("/api/admin/chargebacks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("fraud"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCorrelatedChargebacks_AsAdmin_ShouldReturnData() throws Exception {
        UUID userId = UUID.randomUUID();
        Chargeback cb = Chargeback.builder()
                .id(UUID.randomUUID())
                .reason("correlated")
                .build();
        when(chargebackService.findCorrelatedChargebacksForUser(userId.getLeastSignificantBits())).thenReturn(List.of(cb));

        mockMvc.perform(get("/api/admin/chargebacks/correlated/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("correlated"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllChargebacks_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/chargebacks"))
                .andExpect(status().isForbidden());
    }
}









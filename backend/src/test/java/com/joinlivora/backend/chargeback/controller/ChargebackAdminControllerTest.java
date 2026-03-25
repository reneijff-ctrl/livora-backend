package com.joinlivora.backend.chargeback.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.chargeback.InternalChargebackService;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.config.SecurityConfig;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChargebackAdminController.class)
@Import(SecurityConfig.class)
class ChargebackAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InternalChargebackService chargebackService;

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
    void getAllChargebacks_AsAdmin_ShouldReturnData() throws Exception {
        ChargebackCase c = ChargebackCase.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .status(ChargebackStatus.OPEN)
                .build();
        
        when(chargebackService.getAllChargebacks()).thenReturn(List.of(c));

        mockMvc.perform(get("/internal/chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(50.0))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargeback_Found_ShouldReturnData() throws Exception {
        UUID id = UUID.randomUUID();
        ChargebackCase c = ChargebackCase.builder()
                .id(id)
                .amount(new BigDecimal("50.00"))
                .status(ChargebackStatus.OPEN)
                .build();

        when(chargebackService.getChargebackById(id)).thenReturn(Optional.of(c));

        mockMvc.perform(get("/internal/chargebacks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amount").value(50.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargeback_NotFound_ShouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        when(chargebackService.getChargebackById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/chargebacks/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_ShouldReturnUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        ChargebackCase updated = ChargebackCase.builder()
                .id(id)
                .status(ChargebackStatus.WON)
                .build();

        when(chargebackService.updateStatus(id, ChargebackStatus.WON)).thenReturn(updated);

        mockMvc.perform(patch("/internal/chargebacks/" + id + ".status")
                        .param("status", "WON")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllChargebacks_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/internal/chargebacks"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllChargebacks_Anonymous_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/chargebacks"))
                .andExpect(status().isUnauthorized());
    }
}









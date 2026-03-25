package com.joinlivora.backend.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimitRequest;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPayoutControlController.class)
@org.springframework.context.annotation.Import(com.joinlivora.backend.config.SecurityConfig.class)
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class AdminPayoutControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatorPayoutStateRepository creatorPayoutStateRepository;

    @MockBean
    private PayoutPolicyAuditService payoutPolicyAuditService;

    @MockBean
    private com.joinlivora.backend.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.user.UserService userService;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @BeforeEach
    void setup() throws Exception {
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

        User admin = new User();
        admin.setId(10L);
        admin.setEmail("admin@test.com");
        when(userService.getByEmail("user")).thenReturn(admin);
    }

    @Test
    void getPayoutState_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        CreatorPayoutState state = CreatorPayoutState.builder()
                .creatorId(creatorId)
                .status(PayoutStateStatus.ACTIVE)
                .frequency(PayoutFrequency.NO_LIMIT)
                .build();

        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(state));

        mockMvc.perform(get("/api/admin/payouts/state/" + creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.frequency").value("NO_LIMIT"));
    }

    @Test
    void setLimit_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        PayoutLimitRequest request = new PayoutLimitRequest(new BigDecimal("100.00"), PayoutFrequency.DAILY);

        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
        when(creatorPayoutStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/admin/payouts/limit/" + creatorId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIMITED"))
                .andExpect(jsonPath("$.frequency").value("DAILY"))
                .andExpect(jsonPath("$.currentLimit").value(100.00))
                .andExpect(jsonPath("$.manualOverride").value(true));

        verify(payoutPolicyAuditService).logAdminDecision(eq(creatorId), eq(new BigDecimal("100.00")), eq(PayoutFrequency.DAILY), anyString());
    }

    @Test
    void unpause_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();

        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
        when(creatorPayoutStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/admin/payouts/unpause/" + creatorId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.frequency").value("NO_LIMIT"))
                .andExpect(jsonPath("$.manualOverride").value(true));

        verify(payoutPolicyAuditService).logAdminDecision(eq(creatorId), isNull(), eq(PayoutFrequency.NO_LIMIT), anyString());
    }
}









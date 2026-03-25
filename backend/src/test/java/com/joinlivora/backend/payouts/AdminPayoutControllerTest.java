package com.joinlivora.backend.payouts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.payouts.dto.FreezeRequest;
import com.joinlivora.backend.payouts.service.PayoutFreezeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPayoutController.class)
@Import(com.joinlivora.backend.config.SecurityConfig.class)
class AdminPayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private PayoutFreezeService payoutFreezeService;

    @MockBean
    private com.joinlivora.backend.user.UserService userService;

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
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void freeze_ShouldCallService() throws Exception {
        UUID creatorId = UUID.randomUUID();
        FreezeRequest request = new FreezeRequest("Suspicious activity");

        mockMvc.perform(post("/admin/payouts/{creatorId}/freeze", creatorId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payouts frozen for creator " + creatorId));

        verify(payoutFreezeService).freezeCreator(eq(creatorId), eq("Suspicious activity"), eq("admin@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void unfreeze_ShouldCallService() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(post("/admin/payouts/{creatorId}/unfreeze", creatorId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payouts unfrozen for creator " + creatorId));

        verify(payoutFreezeService).unfreezeCreator(eq(creatorId), eq("admin@test.com"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void freeze_WhenNotAdmin_ShouldReturnForbidden() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(post("/admin/payouts/{creatorId}/freeze", creatorId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}









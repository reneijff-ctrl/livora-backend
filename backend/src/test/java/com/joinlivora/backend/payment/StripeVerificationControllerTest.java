package com.joinlivora.backend.payment;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.LoginFailureHandler;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StripeWebhookController.class)
@Import(SecurityConfig.class)
class StripeVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookEventRepository webhookEventRepository;
    @MockBean
    private StripeWebhookService stripeWebhookService;
    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

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
    private AuditService auditService;
    @MockBean
    private AuditLogoutHandler auditLogoutHandler;
    @MockBean
    private LoginFailureHandler loginFailureHandler;
    @MockBean
    private UserRiskStateRepository userRiskStateRepository;
    @MockBean
    private UserRepository userRepository;

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
    @WithMockUser
    void verifySession_Paid_ShouldReturnSuccess() throws Exception {
        Session session = mock(Session.class);
        when(session.getPaymentStatus()).thenReturn("paid");
        when(session.getMode()).thenReturn("payment");
        when(session.getAmountTotal()).thenReturn(500L);
        
        when(stripeClient.checkout().sessions().retrieve(anyString())).thenReturn(session);

        mockMvc.perform(get("/api/payments/verify")
                        .param("session_id", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(500));
    }

    @Test
    @WithMockUser
    void verifySession_Unpaid_ShouldReturnFailed() throws Exception {
        Session session = mock(Session.class);
        when(session.getPaymentStatus()).thenReturn("unpaid");
        when(session.getMode()).thenReturn("payment");
        
        when(stripeClient.checkout().sessions().retrieve(anyString())).thenReturn(session);

        mockMvc.perform(get("/api/payments/verify")
                        .param("session_id", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @WithMockUser
    void verifySession_NotFound_ShouldReturn404() throws Exception {
        StripeException stripeException = mock(StripeException.class);
        when(stripeException.getStatusCode()).thenReturn(404);
        
        when(stripeClient.checkout().sessions().retrieve(anyString())).thenThrow(stripeException);

        mockMvc.perform(get("/api/payments/verify")
                        .param("session_id", "sess_invalid"))
                .andExpect(status().isNotFound());
    }
}









package com.joinlivora.backend.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payout.dto.PayoutHoldOverrideRequest;
import com.joinlivora.backend.payout.dto.PayoutHoldReleaseRequest;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPayoutHoldController.class)
@Import(SecurityConfig.class)
class AdminPayoutHoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayoutHoldAdminService payoutHoldAdminService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private JwtService jwtService;

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
    private com.joinlivora.backend.user.UserRepository userRepository;

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
        
        User admin = new User();
        admin.setEmail("admin@test.com");
        when(userService.getByEmail(anyString())).thenReturn(admin);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideHold_AsAdmin_ShouldReturnNoContent() throws Exception {
        PayoutHoldOverrideRequest request = PayoutHoldOverrideRequest.builder()
                .subjectId(UUID.randomUUID())
                .subjectType(RiskSubjectType.CREATOR)
                .holdLevel(HoldLevel.MEDIUM)
                .holdDays(7)
                .reason("test")
                .build();

        mockMvc.perform(post("/api/admin/payout-hold/override")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(payoutHoldAdminService).overrideHold(any(PayoutHoldOverrideRequest.class), any(User.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void releaseHold_AsAdmin_ShouldReturnNoContent() throws Exception {
        PayoutHoldReleaseRequest request = PayoutHoldReleaseRequest.builder()
                .subjectId(UUID.randomUUID())
                .subjectType(RiskSubjectType.CREATOR)
                .reason("cleared")
                .build();

        mockMvc.perform(post("/api/admin/payout-hold/release")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(payoutHoldAdminService).releaseHold(any(PayoutHoldReleaseRequest.class), any(User.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void overrideHold_AsUser_ShouldReturnForbidden() throws Exception {
        PayoutHoldOverrideRequest request = PayoutHoldOverrideRequest.builder()
                .subjectId(UUID.randomUUID())
                .subjectType(RiskSubjectType.CREATOR)
                .holdLevel(HoldLevel.MEDIUM)
                .holdDays(7)
                .reason("test")
                .build();

        mockMvc.perform(post("/api/admin/payout-hold/override")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}









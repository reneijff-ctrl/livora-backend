package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.dto.IpReputation;
import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.IpReputationService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSecurityController.class)
@Import(SecurityConfig.class)
class AdminSecurityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceFingerprintRepository deviceFingerprintRepository;

    @MockBean
    private IpReputationService ipReputationService;

    @MockBean
    private RuleFraudSignalRepository fraudSignalRepository;

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
    void getUserDevices_AsAdmin_ShouldReturnData() throws Exception {
        Long userId = 123L;
        UUID userUuid = new UUID(0L, userId);
        
        DeviceFingerprint fingerprint = DeviceFingerprint.builder()
                .userId(userId)
                .fingerprintHash("hash123")
                .ipAddress("1.2.3.4")
                .build();
        
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .userId(userId)
                .reason("Reason")
                .build();

        when(deviceFingerprintRepository.findAllByUserId(userId)).thenReturn(List.of(fingerprint));
        when(fraudSignalRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(signal));

        mockMvc.perform(get("/api/admin/security/devices/" + userUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fingerprints[0].fingerprintHash").value("hash123"))
                .andExpect(jsonPath("$.signals[0].reason").value("Reason"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getIpSecurity_AsAdmin_ShouldReturnData() throws Exception {
        String ip = "1.2.3.4";
        IpReputation reputation = IpReputation.builder()
                .ip(ip)
                .riskScore(10)
                .build();
        
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .userId(123L)
                .reason("IP: " + ip)
                .build();

        when(ipReputationService.getReputation(ip)).thenReturn(reputation);
        when(fraudSignalRepository.findAllByReasonContaining(ip)).thenReturn(List.of(signal));

        mockMvc.perform(get("/api/admin/security/ip/" + ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputation.ip").value(ip))
                .andExpect(jsonPath("$.signals[0].reason").value("IP: 1.2.3.4"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUserDevices_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/security/devices/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserDevices_Anonymous_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/security/devices/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}









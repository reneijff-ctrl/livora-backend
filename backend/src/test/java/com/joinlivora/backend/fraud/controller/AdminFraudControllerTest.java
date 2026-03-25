package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.dto.ChargebackAdminResponseDTO;
import com.joinlivora.backend.fraud.dto.FailedLoginDTO;
import com.joinlivora.backend.fraud.dto.FraudDashboardMetricsDTO;
import com.joinlivora.backend.fraud.dto.PaymentAnomalyDTO;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.AdminFraudQueryService;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.fraud.service.EnforcementService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.User;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFraudController.class)
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class AdminFraudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleFraudSignalRepository fraudSignalRepository;

    @MockBean
    private FraudDetectionService fraudDetectionService;

    @MockBean
    private UserService userService;

    @MockBean
    private AdminFraudQueryService adminFraudQueryService;

    @MockBean
    private EnforcementService enforcementService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private ChargebackService chargebackService;

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
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

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
    void getAllSignals_AsAdmin_ShouldReturnData() throws Exception {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .id(UUID.randomUUID())
                .userId(1L)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.PAYMENT_FAILURE)
                .reason("Test type")
                .source(FraudSource.PAYMENT)
                .createdAt(Instant.now())
                .build();
        org.springframework.data.domain.Page<RuleFraudSignal> page = new PageImpl<>(List.of(signal), pageable, 1);
        when(fraudSignalRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        User testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@test.com");
        when(userService.getById(anyLong())).thenReturn(testUser);

        mockMvc.perform(get("/api/admin/fraud/signals")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("Test type"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSignalsByUserId_AsAdmin_ShouldReturnData() throws Exception {
        Long userId = 123L;
        UUID userUuid = new UUID(0L, userId);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .riskLevel(FraudDecisionLevel.HIGH)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("Fraud detected")
                .source(FraudSource.SYSTEM)
                .createdAt(Instant.now())
                .build();
        org.springframework.data.domain.Page<RuleFraudSignal> page = new PageImpl<>(List.of(signal), pageable, 1);
        when(fraudSignalRepository.findAllByUserId(eq(userId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        User testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("user@test.com");
        when(userService.getById(anyLong())).thenReturn(testUser);

        mockMvc.perform(get("/api/admin/fraud/signals/" + userUuid)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userId.intValue()))
                .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllSignals_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/fraud/signals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void resolveSignal_AsAdmin_ShouldReturnNoContent() throws Exception {
        UUID signalId = UUID.randomUUID();
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setId(1L);

        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        mockMvc.perform(post("/api/admin/fraud/signals/" + signalId + "/resolve")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(fraudDetectionService).resolveSignal(eq(signalId), any(User.class));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void overrideFraudRisk_AsAdmin_ShouldReturnNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setId(1L);
        User user = new User();
        user.setId(userId.getLeastSignificantBits());

        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(userService.getById(anyLong())).thenReturn(user);

        mockMvc.perform(post("/api/admin/fraud/users/" + userId + "/fraud-risk")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"riskLevel\": \"HIGH\"}"))
                .andExpect(status().isNoContent());

        verify(fraudDetectionService).overrideRiskLevel(eq(user), eq(com.joinlivora.backend.user.FraudRiskLevel.HIGH), any(User.class));
        verify(enforcementService).recordManualOverride(eq(userId), contains("Risk level override"), eq("ADMIN"), eq("INTERNAL"), any());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void overrideFraudRisk_DowngradeWithUnresolvedHighRisk_ShouldReturnBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = new User();
        admin.setEmail("admin@test.com");
        User user = new User();
        user.setId(userId.getLeastSignificantBits());
        user.setFraudRiskLevel(com.joinlivora.backend.user.FraudRiskLevel.HIGH);

        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(userService.getById(anyLong())).thenReturn(user);
        doThrow(new IllegalStateException("Cannot downgrade risk level because unresolved HIGH-risk signals exist."))
                .when(fraudDetectionService).overrideRiskLevel(any(), eq(com.joinlivora.backend.user.FraudRiskLevel.LOW), any());

        mockMvc.perform(post("/api/admin/fraud/users/" + userId + "/fraud-risk")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"riskLevel\": \"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot downgrade risk level because unresolved HIGH-risk signals exist."));
    }
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void unblockUser_AsAdmin_ShouldReturnNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setId(1L);
        User user = new User();
        user.setId(userId.getLeastSignificantBits());

        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(userService.getById(anyLong())).thenReturn(user);

        mockMvc.perform(post("/api/admin/fraud/users/" + userId + "/unblock")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(fraudDetectionService).unblockUser(eq(user), any(User.class));
        verify(enforcementService).recordManualOverride(eq(userId), contains("Manual unblock"), eq("ADMIN"), eq("INTERNAL"), any());
    }
    @Test
    @WithMockUser(roles = "ADMIN")
    void getFraudHistory_ShouldReturnData() throws Exception {
        UUID userId = UUID.randomUUID();
        com.joinlivora.backend.fraud.model.FraudEvent event = com.joinlivora.backend.fraud.model.FraudEvent.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .eventType(com.joinlivora.backend.fraud.model.FraudEventType.CHARGEBACK_REPORTED)
                .reason("Fraudulent")
                .createdAt(Instant.now())
                .build();

        when(adminFraudQueryService.getFraudHistory(userId)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/admin/fraud/users/" + userId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Fraudulent"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsersWithEnforcement_ShouldReturnData() throws Exception {
        com.joinlivora.backend.fraud.model.FraudScore score = com.joinlivora.backend.fraud.model.FraudScore.builder()
                .userId(1L)
                .score(80)
                .riskLevel("CRITICAL")
                .calculatedAt(Instant.now())
                .build();

        when(adminFraudQueryService.getUsersWithEnforcement()).thenReturn(List.of(score));

        mockMvc.perform(get("/api/admin/fraud/active-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].riskLevel").value("CRITICAL"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRiskScore_ShouldReturnData() throws Exception {
        UUID userId = UUID.randomUUID();
        com.joinlivora.backend.fraud.model.RiskScore riskScore = com.joinlivora.backend.fraud.model.RiskScore.builder()
                .userId(userId)
                .score(75)
                .breakdown("CHARGEBACK_COUNT(1), MULTIPLE_ACCOUNTS(1)")
                .lastEvaluatedAt(Instant.now())
                .build();

        when(adminFraudQueryService.getRiskScore(userId)).thenReturn(java.util.Optional.of(riskScore));

        mockMvc.perform(get("/api/admin/fraud/users/" + userId + "/risk-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.breakdown").value("CHARGEBACK_COUNT(1), MULTIPLE_ACCOUNTS(1)"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRiskScore_NotFound_ShouldReturn404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminFraudQueryService.getRiskScore(userId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/admin/fraud/users/" + userId + "/risk-score"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsersByFraudRiskLevel_ShouldReturnData() throws Exception {
        UserAdminResponseDTO userDto = UserAdminResponseDTO.builder()
                .id(1L)
                .email("fraud@example.com")
                .fraudRiskLevel(com.joinlivora.backend.user.FraudRiskLevel.HIGH)
                .build();

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(adminFraudQueryService.getUsersByFraudRiskLevel(eq(com.joinlivora.backend.user.FraudRiskLevel.HIGH), any()))
                .thenReturn(new PageImpl<>(List.of(userDto), pageable, 1));

        mockMvc.perform(get("/api/admin/fraud/users").param("riskLevel", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("fraud@example.com"))
                .andExpect(jsonPath("$.content[0].fraudRiskLevel").value("HIGH"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getFailedLogins_ShouldReturnData() throws Exception {
        FailedLoginDTO loginDto = FailedLoginDTO.builder()
                .email("failed@example.com")
                .ipAddress("127.0.0.1")
                .timestamp(Instant.now())
                .build();

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(adminFraudQueryService.getFailedLogins(any()))
                .thenReturn(new PageImpl<>(List.of(loginDto), pageable, 1));

        mockMvc.perform(get("/api/admin/fraud/failed-logins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("failed@example.com"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("127.0.0.1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPaymentAnomalies_ShouldReturnData() throws Exception {
        PaymentAnomalyDTO anomalyDto = PaymentAnomalyDTO.builder()
                .paymentId(UUID.randomUUID())
                .userEmail("anomalous@example.com")
                .amount(new BigDecimal("1000.00"))
                .riskLevel(RiskLevel.CRITICAL)
                .build();

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(adminFraudQueryService.getPaymentAnomalies(any()))
                .thenReturn(new PageImpl<>(List.of(anomalyDto), pageable, 1));

        mockMvc.perform(get("/api/admin/fraud/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userEmail").value("anomalous@example.com"))
                .andExpect(jsonPath("$.content[0].riskLevel").value("CRITICAL"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChargebackHistory_ShouldReturnData() throws Exception {
        ChargebackAdminResponseDTO cbDto = ChargebackAdminResponseDTO.builder()
                .userEmail("cb@example.com")
                .amount(new BigDecimal("50.00"))
                .reason("fraud")
                .build();

        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(chargebackService.getFraudChargebacks(any()))
                .thenReturn(new PageImpl<>(List.of(cbDto), pageable, 1));

        mockMvc.perform(get("/api/admin/fraud/chargebacks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userEmail").value("cb@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboardMetrics_ShouldReturnData() throws Exception {
        FraudDashboardMetricsDTO metrics = FraudDashboardMetricsDTO.builder()
                .unresolvedSignals(10)
                .criticalSignals(2)
                .highSignals(5)
                .enforcementLast24h(3)
                .build();

        when(adminFraudQueryService.getFraudDashboardMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/admin/fraud/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unresolvedSignals").value(10))
                .andExpect(jsonPath("$.criticalSignals").value(2))
                .andExpect(jsonPath("$.highSignals").value(5))
                .andExpect(jsonPath("$.enforcementLast24h").value(3));
    }
}









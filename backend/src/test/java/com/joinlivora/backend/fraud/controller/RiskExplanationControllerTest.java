package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskExplanationAuditService;
import com.joinlivora.backend.fraud.service.RiskExplanationService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskExplanationController.class)
@Import(SecurityConfig.class)
class RiskExplanationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskExplanationService riskExplanationService;

    @MockBean
    private RiskExplanationAuditService riskExplanationAuditService;

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
    @WithMockUser(username = "creator@test.com")
    void getMyLatestExplanation_Success_ShouldReturnRedactedExplanation() throws Exception {
        User user = new User();
        user.setId(123L);
        user.setEmail("creator@test.com");
        
        UUID userUuid = new UUID(0L, 123L);
        // Explanation containing internal model name, threshold, and AI confidence score
        String internalText = "[MEDIUM] [AML Engine] Payout requested by a recently created account (last 7 days). " +
                "Model v2.1 detected risk with confidence 0.85. Threshold 0.8 exceeded.";
        
        RiskExplanation explanation = RiskExplanation.builder()
                .id(UUID.randomUUID())
                .subjectType(RiskSubjectType.USER)
                .subjectId(userUuid)
                .riskScore(75)
                .decision(RiskDecision.REVIEW)
                .explanationText(internalText)
                .generatedAt(Instant.now())
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(riskExplanationService.getLatestExplanationForSubject(userUuid, RiskSubjectType.USER))
                .thenReturn(Optional.of(explanation));

        mockMvc.perform(get("/api/risk/why"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.containsString("System-detected risk patterns")))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("AML Engine"))))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Model v2.1"))))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("confidence 0.85"))))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Threshold 0.8"))));

        org.mockito.Mockito.verify(riskExplanationAuditService).logRequest(
                org.mockito.ArgumentMatchers.eq(userUuid), 
                org.mockito.ArgumentMatchers.any(), 
                org.mockito.ArgumentMatchers.eq(explanation.getId()));
    }

    @Test
    @WithMockUser(username = "creator@test.com")
    void getMyLatestExplanation_CommonTemplates_ShouldBeRedacted() throws Exception {
        User user = new User();
        user.setId(123L);
        user.setEmail("creator@test.com");
        UUID userUuid = new UUID(0L, 123L);

        // Case: [LOW] [Trust Evaluation] Action performed from a new device (current session)
        String templateText = "[LOW] [Trust Evaluation] Action performed from a new device (current session)";
        
        RiskExplanation explanation = RiskExplanation.builder()
                .subjectId(userUuid)
                .subjectType(RiskSubjectType.USER)
                .explanationText(templateText)
                .decision(RiskDecision.ALLOW)
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(riskExplanationService.getLatestExplanationForSubject(userUuid, RiskSubjectType.USER))
                .thenReturn(Optional.of(explanation));

        mockMvc.perform(get("/api/risk/why"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanationText").value("[LOW] System-detected risk patterns Action performed from a new device (current session)"))
                .andExpect(jsonPath("$.explanationText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Trust Evaluation"))));
    }

    @Test
    @WithMockUser(username = "creator@test.com")
    void getMyLatestExplanation_NotFound_ShouldReturn404() throws Exception {
        User user = new User();
        user.setId(123L);
        user.setEmail("creator@test.com");
        UUID userUuid = new UUID(0L, 123L);

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(riskExplanationService.getLatestExplanationForSubject(userUuid, RiskSubjectType.USER))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/risk/why"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No risk explanation found for your account."));
    }

    @Test
    void getMyLatestExplanation_Anonymous_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/risk/why"))
                .andExpect(status().isUnauthorized());
    }
}









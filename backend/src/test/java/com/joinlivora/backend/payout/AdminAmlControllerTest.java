package com.joinlivora.backend.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payout.dto.AmlOverrideRequest;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAmlController.class)
@Import(SecurityConfig.class)
@org.springframework.data.web.config.EnableSpringDataWebSupport
class AdminAmlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayoutRiskRepository payoutRiskRepository;

    @MockBean
    private PayoutAbuseDetectionService payoutAbuseDetectionService;

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
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllRisks_AsAdmin_ShouldReturnData() throws Exception {
        PayoutRisk risk = PayoutRisk.builder()
                .id(UUID.randomUUID())
                .userId(1L)
                .riskScore(50)
                .reasons("Test")
                .lastEvaluatedAt(Instant.now())
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(payoutRiskRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(risk), pageable, 1));

        mockMvc.perform(get("/api/admin/aml/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].riskScore").value(50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRisksByUserId_AsAdmin_ShouldReturnData() throws Exception {
        UUID userId = UUID.randomUUID();
        PayoutRisk risk = PayoutRisk.builder()
                .id(UUID.randomUUID())
                .userId(userId.getLeastSignificantBits())
                .riskScore(75)
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(payoutRiskRepository.findAllByUserIdOrderByLastEvaluatedAtDesc(eq(userId.getLeastSignificantBits()), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(risk), pageable, 1));

        mockMvc.perform(get("/api/admin/aml/risks/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].riskScore").value(75));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideRisk_AsAdmin_ShouldSucceed() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId.getLeastSignificantBits());
        user.setEmail("creator@test.com");

        User admin = new User();
        admin.setId(10L);
        admin.setEmail("admin@test.com");

        AmlOverrideRequest request = new AmlOverrideRequest(0, "Verified creator");

        when(userService.getById(userId.getLeastSignificantBits())).thenReturn(user);
        when(userService.getByEmail("user")).thenReturn(admin);

        mockMvc.perform(post("/api/admin/aml/override/" + userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(payoutAbuseDetectionService).override(eq(user), eq(0), eq("Verified creator"), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllRisks_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/aml/risks"))
                .andExpect(status().isForbidden());
    }
}









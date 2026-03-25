package com.joinlivora.backend.reputation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.reputation.dto.ReputationAdjustmentRequest;
import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationStatus;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReputationController.class)
@org.springframework.context.annotation.Import(com.joinlivora.backend.config.SecurityConfig.class)
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class AdminReputationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.user.UserService userService;

    @MockBean
    private ReputationEventService reputationEventService;

    @MockBean
    private ReputationEventRepository reputationEventRepository;

    @MockBean
    private CreatorReputationSnapshotRepository snapshotRepository;

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
    }

    @Test
    void adjustReputation_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        ReputationAdjustmentRequest request = new ReputationAdjustmentRequest(10, "Good conduct");

        mockMvc.perform(post("/api/admin/reputation/adjust/" + creatorId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(reputationEventService).recordEvent(eq(creatorId), any(), eq(10), any(), any());
    }

    @Test
    void getTimeline_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(reputationEventRepository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/reputation/timeline/" + creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSnapshot_Success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(80)
                .status(ReputationStatus.TRUSTED)
                .build();

        when(snapshotRepository.findById(creatorId)).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/api/admin/reputation/snapshot/" + creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentScore").value(80))
                .andExpect(jsonPath("$.status").value("TRUSTED"));
    }

    @Test
    void getSnapshot_NotFound() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(snapshotRepository.findById(creatorId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/reputation/snapshot/" + creatorId))
                .andExpect(status().isNotFound());
    }
}









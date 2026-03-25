package com.joinlivora.backend.monetization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.monetization.dto.CollusionOverrideRequest;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCollusionController.class)
@Import(SecurityConfig.class)
@org.springframework.data.web.config.EnableSpringDataWebSupport
class AdminCollusionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatorCollusionRecordRepository recordRepository;

    @MockBean
    private CreatorTrustService trustService;

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
    private com.joinlivora.backend.user.UserRepository userRepository;

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
    void getAllRecords_AsAdmin_ShouldReturnData() throws Exception {
        CreatorCollusionRecord record = CreatorCollusionRecord.builder()
                .id(UUID.randomUUID())
                .creatorId(UUID.randomUUID())
                .score(80)
                .detectedPattern("TEST")
                .evaluatedAt(Instant.now())
                .build();

        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(recordRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), pageable, 1));

        mockMvc.perform(get("/api/admin/collusion/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].score").value(80));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRecordsByCreatorId_AsAdmin_ShouldReturnData() throws Exception {
        UUID creatorId = UUID.randomUUID();
        CreatorCollusionRecord record = CreatorCollusionRecord.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .score(90)
                .build();

        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(recordRepository.findAllByCreatorIdOrderByEvaluatedAtDesc(eq(creatorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), pageable, 1));

        mockMvc.perform(get("/api/admin/collusion/" + creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].score").value(90));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideCollusion_AsAdmin_ShouldSucceed() throws Exception {
        UUID creatorId = UUID.randomUUID();
        User creator = new User();
        creator.setId(creatorId.getLeastSignificantBits());
        creator.setEmail("creator@test.com");

        User admin = new User();
        admin.setEmail("admin@test.com");

        CollusionOverrideRequest request = new CollusionOverrideRequest(10, "Verified manually");

        when(userService.getById(creatorId.getLeastSignificantBits())).thenReturn(creator);
        when(userService.getByEmail("creator")).thenReturn(admin);

        mockMvc.perform(post("/api/admin/collusion/override/" + creatorId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(trustService).override(eq(creator), eq(10), eq("Verified manually"), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllRecords_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/collusion/creators"))
                .andExpect(status().isForbidden());
    }
}









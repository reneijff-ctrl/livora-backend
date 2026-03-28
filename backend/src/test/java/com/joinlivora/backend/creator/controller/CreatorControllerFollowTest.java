package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.controller.CreatorController;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.CreatorSearchService;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;

@WebMvcTest(CreatorController.class)
@Import(SecurityConfig.class)
class CreatorControllerFollowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @MockBean
    private CreatorSearchService creatorSearchService;

    @MockBean
    private com.joinlivora.backend.creator.service.CreatorPostService creatorPostService;

    @MockBean
    private CreatorFollowService followService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.content.ContentService contentService;

    @MockBean
    private com.joinlivora.backend.content.MediaUnlockService mediaUnlockService;

    @MockBean
    private com.joinlivora.backend.websocket.PresenceService presenceService;

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
    void getFollowerCount_ShouldReturnCount() throws Exception {
        when(creatorProfileService.getFollowerCountStrict("2")).thenReturn(10L);

        mockMvc.perform(get("/api/creators/2/followers/count"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("10"));
    }
}









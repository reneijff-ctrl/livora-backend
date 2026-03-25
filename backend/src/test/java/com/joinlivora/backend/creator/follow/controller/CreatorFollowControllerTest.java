package com.joinlivora.backend.creator.follow.controller;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.UserPrincipal;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreatorFollowController.class)
@Import(SecurityConfig.class)
class CreatorFollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorFollowService creatorFollowService;

    @MockBean
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @MockBean
    private UserService userService;

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

    private User follower;
    private User creator;
    private UserPrincipal principal;

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

        follower = new User();
        follower.setId(1L);
        follower.setEmail("follower@test.com");
        follower.setRole(com.joinlivora.backend.user.Role.USER);

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
        creator.setRole(com.joinlivora.backend.user.Role.CREATOR);

        principal = new UserPrincipal(follower);

        when(userService.getById(1L)).thenReturn(follower);
        when(creatorProfileService.getUserByProfileId(2L)).thenReturn(creator);
    }

    @Test
    void follow_ShouldReturnStatus() throws Exception {
        when(creatorFollowService.isFollowing(follower, creator)).thenReturn(true);
        when(creatorFollowService.getFollowerCount(creator)).thenReturn(10L);

        mockMvc.perform(post("/api/creators/2/follow")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followers").value(10));

        verify(creatorFollowService).follow(follower, creator);
    }

    @Test
    void unfollow_ShouldReturnStatus() throws Exception {
        when(creatorFollowService.isFollowing(follower, creator)).thenReturn(false);
        when(creatorFollowService.getFollowerCount(creator)).thenReturn(9L);

        mockMvc.perform(delete("/api/creators/2/follow")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followers").value(9));

        verify(creatorFollowService).unfollow(follower, creator);
    }

    @Test
    void getStatus_ShouldReturnStatus() throws Exception {
        when(creatorFollowService.isFollowing(follower, creator)).thenReturn(true);
        when(creatorFollowService.getFollowerCount(creator)).thenReturn(15L);

        mockMvc.perform(get("/api/creators/2/follow/status")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followers").value(15));
    }

    @Test
    void getStatus_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/creators/2/follow/status"))
                .andExpect(status().isUnauthorized());
    }
}









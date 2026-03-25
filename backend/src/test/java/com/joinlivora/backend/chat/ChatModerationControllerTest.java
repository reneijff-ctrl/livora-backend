package com.joinlivora.backend.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatModerationController.class)
@org.springframework.context.annotation.Import(com.joinlivora.backend.config.SecurityConfig.class)
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class ChatModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatModerationService moderationService;

    @MockBean
    private SlowModeBypassService slowModeBypassService;

    @MockBean
    private PPVChatAccessService ppvChatAccessService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.user.UserService userService;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.streaming.StreamRepository StreamRepository;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws jakarta.servlet.ServletException, java.io.IOException {
        com.joinlivora.backend.user.User admin = new com.joinlivora.backend.user.User();
        admin.setId(999L);
        admin.setEmail("creator"); // Default senderEmail for @WithMockUser is "creator"
        admin.setRole(com.joinlivora.backend.user.Role.ADMIN);
        org.mockito.Mockito.when(userService.getByEmail(any())).thenReturn(admin);

        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(funnelTrackingFilter).doFilter(any(), any(), any());
    }

    @Test
    void muteUser_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/chat/moderation/mute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"durationSeconds\": 3600, \"roomId\": \"room1\"}"))
                .andExpect(status().isOk());

        verify(moderationService).muteUser(eq(1L), eq(999L), eq(java.time.Duration.ofSeconds(3600)), eq("room1"));
    }

    @Test
    void shadowMuteUser_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/chat/moderation/shadow-mute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"durationSeconds\": 3600, \"roomId\": \"room1\"}"))
                .andExpect(status().isOk());

        verify(moderationService).shadowMuteUser(eq(1L), eq(999L), eq(java.time.Duration.ofSeconds(3600)), eq("room1"));
    }

    @Test
    void banUser_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/chat/moderation/ban")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 2, \"roomId\": \"test-room\"}"))
                .andExpect(status().isOk());

        verify(moderationService).banUser(eq(2L), eq(999L), eq("test-room"));
    }

    @Test
    void deleteMessage_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/chat/moderation/delete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"room1\", \"messageId\": \"msg1\"}"))
                .andExpect(status().isOk());

        verify(moderationService).deleteMessage(eq("room1"), eq("msg1"), eq(999L));
    }

    @Test
    void revokeBypass_AsAdmin_ShouldSucceed() throws Exception {
        java.util.UUID roomId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/chat/moderation/revoke-bypass")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"roomId\": \"" + roomId + "\"}"))
                .andExpect(status().isOk());

        verify(slowModeBypassService).revokeBypass(eq(1L), eq(roomId));
    }

    @Test
    void grantPpvAccess_AsAdmin_ShouldSucceed() throws Exception {
        java.util.UUID roomId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/chat/moderation/grant-ppv-access")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"roomId\": \"" + roomId + "\", \"expiresAt\": \"2026-01-20T10:00:00Z\"}"))
                .andExpect(status().isOk());

        verify(ppvChatAccessService).grantAccess(eq(1L), eq(roomId), any(java.time.Instant.class));
    }

    @Test
    void revokePpvAccess_AsAdmin_ShouldSucceed() throws Exception {
        java.util.UUID roomId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/chat/moderation/revoke-ppv-access")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"roomId\": \"" + roomId + "\"}"))
                .andExpect(status().isOk());

        verify(ppvChatAccessService).revokeAccess(eq(1L), eq(roomId));
    }
}










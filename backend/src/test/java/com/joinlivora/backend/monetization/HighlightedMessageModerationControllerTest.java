package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.user.Role;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HighlightedMessageModerationController.class)
@Import(SecurityConfig.class)
class HighlightedMessageModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HighlightedMessageService highlightedMessageService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

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
    @WithMockUser(roles = "MODERATOR")
    void removeHighlight_AsModerator_ShouldSucceed() throws Exception {
        UUID id = UUID.randomUUID();
        User moderator = new User("mod@test.com", "pass", Role.MODERATOR);
        when(userService.getByEmail("creator")).thenReturn(moderator);

        mockMvc.perform(post("/api/moderation/highlights/" + id + "/remove")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Test type\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Highlight removed successfully"));

        verify(highlightedMessageService).removeHighlight(eq(id), any(), eq("Test type"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void removeHighlight_AsUser_ShouldBeForbidden() throws Exception {
        mockMvc.perform(post("/api/moderation/highlights/" + UUID.randomUUID() + "/remove")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refundHighlight_AsAdmin_ShouldSucceed() throws Exception {
        UUID id = UUID.randomUUID();
        User admin = new User("admin@test.com", "pass", Role.ADMIN);
        when(userService.getByEmail("creator")).thenReturn(admin);

        mockMvc.perform(post("/api/moderation/highlights/" + id + "/refund")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Refund type\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Highlight refunded successfully"));

        verify(highlightedMessageService).refundHighlight(eq(id), any(), eq("Refund type"));
    }
}









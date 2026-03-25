package com.joinlivora.backend.streaming;

import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.dto.UserResponse;
import com.joinlivora.backend.websocket.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamModerationController.class)
@org.springframework.context.annotation.Import(com.joinlivora.backend.config.SecurityConfig.class)
class StreamModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StreamModerationService liveStreamModerationService;

    @MockBean
    private LiveViewerCounterService liveViewerCounterService;

    @MockBean
    private PresenceService presenceService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private ModerationSettingsRepository settingsRepository;

    @MockBean
    private com.joinlivora.backend.chat.ChatModerationService chatModerationService;

    private User creator;

    @BeforeEach
    void setUp() throws Exception {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setRole(Role.CREATOR);

        when(userService.getByEmail(any())).thenReturn(creator);

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
    @WithMockUser(roles = "CREATOR")
    void getViewers_ShouldReturnList() throws Exception {
        when(presenceService.getCreatorViewerList(1L)).thenReturn(List.of(
                new UserResponse(2L, "viewer1@test.com", Role.USER)
        ));

        mockMvc.perform(get("/api/liveStream/moderation/viewers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].email").value("viewer1@test.com"));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void muteUser_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/liveStream/moderation/mute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\": 1, \"userId\": 2, \"durationMinutes\": 30}"))
                .andExpect(status().isOk());

        verify(liveStreamModerationService).muteUser(1L, 2L, 30);
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void shadowMuteUser_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/liveStream/moderation/shadow-mute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\": 1, \"userId\": 2}"))
                .andExpect(status().isOk());

        verify(liveStreamModerationService).shadowMuteUser(1L, 2L);
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void kickUser_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/liveStream/moderation/kick")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\": 1, \"userId\": 2}"))
                .andExpect(status().isOk());

        verify(liveStreamModerationService).kickUser(1L, 2L);
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void action_ForbiddenForOtherCreator() throws Exception {
        mockMvc.perform(post("/api/liveStream/moderation/kick")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\": 99, \"userId\": 2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getSettings_ShouldReturnSettings() throws Exception {
        ModerationSettings settings = ModerationSettings.builder()
                .creatorUserId(1L)
                .bannedWords("word1")
                .strictMode(true)
                .build();
        when(settingsRepository.findByCreatorUserId(1L)).thenReturn(java.util.Optional.of(settings));

        mockMvc.perform(get("/api/liveStream/moderation/settings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorUserId").value(1))
                .andExpect(jsonPath("$.strictMode").value(true))
                .andExpect(jsonPath("$.bannedWords").value("word1"));
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void saveSettings_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/liveStream/moderation/settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorUserId\": 1, \"bannedWords\": [\"word1\"], \"autoPinLargeTips\": true, \"aiHighlightEnabled\": true, \"strictMode\": true}"))
                .andExpect(status().isOk());

        verify(settingsRepository).save(any());
        verify(chatModerationService).invalidateCreatorCache(1L);
    }
}









package com.joinlivora.backend.privateshow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorPrivateSettingsController.class)
@Import(SecurityConfig.class)
class CreatorPrivateSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatorPrivateSettingsService settingsService;

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
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    private UserPrincipal creatorPrincipal;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        creatorPrincipal = new UserPrincipal(1L, "creator@test.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CREATOR")));

        userPrincipal = new UserPrincipal(2L, "user@test.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        // Mock filters to bypass them
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
    void getSettings_ShouldReturnSettings() throws Exception {
        CreatorPrivateSettings settings = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(false)
                .pricePerMinute(50L)
                .build();

        when(settingsService.getOrCreate(1L)).thenReturn(settings);

        mockMvc.perform(get("/api/private-settings")
                        .with(user(creatorPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.pricePerMinute").value(50));
    }

    @Test
    void updateSettings_ShouldUpdateAndReturn() throws Exception {
        CreatorPrivateSettings updated = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(true)
                .pricePerMinute(100L)
                .build();

        when(settingsService.updateWithSpy(eq(1L), eq(true), eq(100L), eq(false), isNull(), isNull())).thenReturn(updated);

        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "pricePerMinute", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.pricePerMinute").value(100));
    }

    @Test
    void updateSettings_WithFullSpySettings_ShouldUpdateAll() throws Exception {
        CreatorPrivateSettings updated = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(true)
                .pricePerMinute(100L)
                .allowSpyOnPrivate(true)
                .spyPricePerMinute(25L)
                .maxSpyViewers(5)
                .build();

        when(settingsService.updateWithSpy(eq(1L), eq(true), eq(100L), eq(true), eq(25L), eq(5))).thenReturn(updated);

        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "pricePerMinute", 100,
                                "allowSpyOnPrivate", true,
                                "spyPricePerMinute", 25,
                                "maxSpyViewers", 5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowSpyOnPrivate").value(true))
                .andExpect(jsonPath("$.spyPricePerMinute").value(25))
                .andExpect(jsonPath("$.maxSpyViewers").value(5));
    }

    @Test
    void updateSettings_WithNullMaxSpyViewers_ShouldAcceptNull() throws Exception {
        CreatorPrivateSettings updated = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(true)
                .pricePerMinute(100L)
                .allowSpyOnPrivate(true)
                .spyPricePerMinute(25L)
                .maxSpyViewers(null)
                .build();

        when(settingsService.updateWithSpy(eq(1L), eq(true), eq(100L), eq(true), eq(25L), isNull())).thenReturn(updated);

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", true);
        body.put("pricePerMinute", 100);
        body.put("allowSpyOnPrivate", true);
        body.put("spyPricePerMinute", 25);
        body.put("maxSpyViewers", null);

        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSpyViewers").isEmpty());
    }

    @Test
    void updateSettings_WithEmptyStringMaxSpyViewers_ShouldConvertToNull() throws Exception {
        CreatorPrivateSettings updated = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(true)
                .pricePerMinute(100L)
                .allowSpyOnPrivate(true)
                .spyPricePerMinute(25L)
                .maxSpyViewers(null)
                .build();

        when(settingsService.updateWithSpy(eq(1L), eq(true), eq(100L), eq(true), eq(25L), isNull())).thenReturn(updated);

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", true);
        body.put("pricePerMinute", 100);
        body.put("allowSpyOnPrivate", true);
        body.put("spyPricePerMinute", 25);
        body.put("maxSpyViewers", "");

        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSpyViewers").isEmpty());
    }

    @Test
    void updateSettings_WithMissingSpyFields_ShouldUseDefaults() throws Exception {
        CreatorPrivateSettings updated = CreatorPrivateSettings.builder()
                .creatorId(1L)
                .enabled(true)
                .pricePerMinute(50L)
                .allowSpyOnPrivate(false)
                .build();

        when(settingsService.updateWithSpy(eq(1L), eq(true), eq(50L), eq(false), isNull(), isNull())).thenReturn(updated);

        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "pricePerMinute", 50
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowSpyOnPrivate").value(false));
    }

    @Test
    void updateSettings_WithNegativeSpyPrice_ShouldReturn400() throws Exception {
        mockMvc.perform(patch("/api/private-settings")
                        .with(user(creatorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "pricePerMinute", 100,
                                "allowSpyOnPrivate", true,
                                "spyPricePerMinute", -5
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSettings_NonCreator_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/private-settings")
                        .with(user(userPrincipal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSettings_NonCreator_ShouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/private-settings")
                        .with(user(userPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "pricePerMinute", 100
                        ))))
                .andExpect(status().isForbidden());
    }
}

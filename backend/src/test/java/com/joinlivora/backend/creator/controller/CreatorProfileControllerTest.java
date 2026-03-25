package com.joinlivora.backend.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.creator.dto.CreatorProfileDTO;
import com.joinlivora.backend.creator.dto.UpdateCreatorProfileRequest;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.LoginFailureHandler;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.UserRepository;

@WebMvcTest(CreatorProfileController.class)
@Import(SecurityConfig.class)
class CreatorProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatorProfileService creatorProfileService;

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
    private AuditService auditService;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private LoginFailureHandler loginFailureHandler;

    @MockBean
    private UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws jakarta.servlet.ServletException, java.io.IOException {
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
    void getProfile_ShouldReturnProfile() throws Exception {
        Long userId = 1L;
        UserPrincipal principal = new UserPrincipal(userId, "creator@test.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CREATOR")));

        CreatorProfileDTO dto = CreatorProfileDTO.builder()
                .userId(userId)
                .displayName("Test Creator")
                .username("testcreator")
                .bio("Test Bio")
                .build();

        when(creatorProfileService.getMyProfile(userId)).thenReturn(dto);

        mockMvc.perform(get("/api/creator/profile")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test Creator"))
                .andExpect(jsonPath("$.username").value("testcreator"))
                .andExpect(jsonPath("$.bio").value("Test Bio"));
    }

    @Test
    void updateProfile_ShouldReturnUpdatedProfile() throws Exception {
        Long userId = 1L;
        UserPrincipal principal = new UserPrincipal(userId, "creator@test.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CREATOR")));

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("Updated Name")
                .username("updatedusername")
                .bio("Updated Bio")
                .gender("Male")
                .interestedIn("Everyone")
                .languages("English, Dutch")
                .location("Amsterdam")
                .bodyType("Athletic")
                .ethnicity("Caucasian")
                .eyeColor("Blue")
                .hairColor("Blonde")
                .heightCm(180)
                .weightKg(75)
                .build();

        CreatorProfileDTO updatedDto = CreatorProfileDTO.builder()
                .userId(userId)
                .displayName("Updated Name")
                .username("updatedusername")
                .bio("Updated Bio")
                .gender("Male")
                .interestedIn("Everyone")
                .languages("English, Dutch")
                .location("Amsterdam")
                .bodyType("Athletic")
                .ethnicity("Caucasian")
                .eyeColor("Blue")
                .hairColor("Blonde")
                .heightCm(180)
                .weightKg(75)
                .build();

        when(creatorProfileService.updateProfile(eq(userId), any(UpdateCreatorProfileRequest.class)))
                .thenReturn(updatedDto);

        mockMvc.perform(put("/api/creator/profile")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.username").value("updatedusername"))
                .andExpect(jsonPath("$.gender").value("Male"))
                .andExpect(jsonPath("$.heightCm").value(180));
    }
}









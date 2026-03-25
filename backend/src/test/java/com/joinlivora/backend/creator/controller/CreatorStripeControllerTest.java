package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.creator.dto.CreatorStripeStatusResponse;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.CreatorStripeAccount;
import com.joinlivora.backend.creator.model.StripeOnboardingStatus;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.CreatorStripeAccountService;
import com.joinlivora.backend.creator.service.CreatorStripeService;
import com.joinlivora.backend.security.*;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorStripeController.class)
@Import(SecurityConfig.class)
class CreatorStripeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorStripeService creatorStripeService;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private LoginFailureHandler loginFailureHandler;

    @MockBean
    private AuditService auditService;

    private User creator;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() throws jakarta.servlet.ServletException, java.io.IOException {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setRole(com.joinlivora.backend.user.Role.CREATOR);
        
        principal = new UserPrincipal(creator);

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
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void createStripeAccount_ShouldReturnOnboardingUrl() throws Exception {
        when(userService.getById(1L)).thenReturn(creator);
        when(creatorStripeService.createOrGetStripeAccount(creator)).thenReturn("acct_123");
        when(creatorStripeService.generateOnboardingLink("acct_123")).thenReturn("https://stripe.com/onboard");

        mockMvc.perform(post("/api/creator/stripe/account")
                        .with(csrf())
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stripeAccountId").value("acct_123"))
                .andExpect(jsonPath("$.onboardingUrl").value("https://stripe.com/onboard"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void onboard_ShouldReturnOnboardingUrl() throws Exception {
        when(userService.getById(1L)).thenReturn(creator);
        when(creatorStripeService.createOrGetStripeAccount(creator)).thenReturn("acct_123");
        when(creatorStripeService.generateOnboardingLink("acct_123")).thenReturn("https://stripe.com/onboard");

        mockMvc.perform(post("/api/creator/stripe/onboard")
                        .with(csrf())
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingUrl").value("https://stripe.com/onboard"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void getStatus_ShouldReturnStatus() throws Exception {
        when(userService.getById(1L)).thenReturn(creator);

        CreatorStripeStatusResponse status = CreatorStripeStatusResponse.builder()
                .hasAccount(true)
                .onboardingCompleted(false)
                .payoutsEnabled(false)
                .build();
        when(creatorStripeService.getStripeStatus(creator)).thenReturn(status);

        mockMvc.perform(get("/api/creator/stripe/status")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccount").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.payoutsEnabled").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void onboard_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/creator/stripe/onboard")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}









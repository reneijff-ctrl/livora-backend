package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.adaptive.DynamicTipRecommendationService;
import com.joinlivora.backend.analytics.adaptive.dto.TipRecommendationResponse;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.user.Role;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreatorTipRecommendationController.class)
@Import(SecurityConfig.class)
public class CreatorTipRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamicTipRecommendationService tipRecommendationService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

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
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldReturnTipRecommendation() throws Exception {
        User user = new User("creator@test.com", "password", Role.CREATOR);
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        TipRecommendationResponse response = new TipRecommendationResponse(
                BigDecimal.valueOf(5.00), 0.85, "SAFE", "Test explanation"
        );
        when(tipRecommendationService.generateForCreator(any())).thenReturn(response);

        mockMvc.perform(get("/api/creator/tip/recommendation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMinimumTip").value(5.00))
                .andExpect(jsonPath("$.confidenceScore").value(0.85))
                .andExpect(jsonPath("$.riskLevel").value("SAFE"))
                .andExpect(jsonPath("$.explanation").value("Test explanation"));
    }
}









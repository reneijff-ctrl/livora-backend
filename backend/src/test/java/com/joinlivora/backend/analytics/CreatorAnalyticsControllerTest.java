package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorEarningsBreakdownDTO;
import com.joinlivora.backend.analytics.dto.TopContentDTO;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreatorAnalyticsController.class)
@Import(SecurityConfig.class)
class CreatorAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorAnalyticsService creatorAnalyticsService;

    @MockBean
    private AdaptiveTipEngineService adaptiveTipEngineService;

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
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getAnalytics_AsCreator_ShouldReturnData() throws Exception {
        User creator = new User();
        creator.setEmail("creator@example.com");
        
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        
        CreatorAnalyticsResponseDTO dto = new CreatorAnalyticsResponseDTO(
                from, new BigDecimal("100.00"), 50, 5, 10, 300, 2.5);
        
        when(userService.getByEmail("creator@example.com")).thenReturn(creator);
        when(creatorAnalyticsService.getAnalytics(eq(creator), eq(from), eq(to)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/creator/analytics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$[0].earnings").value(100.00))
                .andExpect(jsonPath("$[0].viewers").value(50))
                .andExpect(jsonPath("$[0].subscriptions").value(5))
                .andExpect(jsonPath("$[0].returningViewers").value(10))
                .andExpect(jsonPath("$[0].avgSessionDuration").value(300))
                .andExpect(jsonPath("$[0].messagesPerViewer").value(2.5));
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getEarningsBreakdown_AsCreator_ShouldReturnData() throws Exception {
        User creator = new User();
        creator.setEmail("creator@example.com");

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);

        CreatorEarningsBreakdownDTO dto = new CreatorEarningsBreakdownDTO(
                new BigDecimal("500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00")
        );

        when(userService.getByEmail("creator@example.com")).thenReturn(creator);
        when(creatorAnalyticsService.getEarningsBreakdown(eq(creator), eq(from), eq(to)))
                .thenReturn(dto);

        mockMvc.perform(get("/api/creator/analytics/earnings-breakdown")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions").value(500.00))
                .andExpect(jsonPath("$.ppv").value(200.00))
                .andExpect(jsonPath("$.tips").value(100.00))
                .andExpect(jsonPath("$.Stream").value(50.00));
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getTopContent_AsCreator_ShouldReturnData() throws Exception {
        User creator = new User();
        creator.setEmail("creator@example.com");

        UUID ppvId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        TopContentDTO dto = new TopContentDTO(
                List.of(new TopContentDTO.ContentRevenueDTO(ppvId, "Top PPV", new BigDecimal("500.00"))),
                List.of(new TopContentDTO.ContentRevenueDTO(roomId, "Top Stream", new BigDecimal("300.00")))
        );

        when(userService.getByEmail("creator@example.com")).thenReturn(creator);
        when(creatorAnalyticsService.getTopContent(eq(creator))).thenReturn(dto);

        mockMvc.perform(get("/api/creator/analytics/top-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topPpvContent[0].title").value("Top PPV"))
                .andExpect(jsonPath("$.topPpvContent[0].revenue").value(500.00))
                .andExpect(jsonPath("$.topliveStreams[0].title").value("Top Stream"))
                .andExpect(jsonPath("$.topliveStreams[0].revenue").value(300.00));
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "USER")
    void getAnalytics_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/analytics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAnalytics_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/creator/analytics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isUnauthorized());
    }
}










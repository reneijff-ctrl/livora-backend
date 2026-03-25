package com.joinlivora.backend.tip;

import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.tip.dto.CreatorTipDto;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorTipController.class)
@Import(SecurityConfig.class)
class CreatorTipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TipService tipService;

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
    @WithMockUser(roles = "CREATOR")
    void getMyTips_ShouldReturnTips() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");

        CreatorTipDto tip = CreatorTipDto.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .fromUserId("u_1***5")
                .createdAt(Instant.now())
                .build();

        when(userService.getByEmail(any())).thenReturn(user);
        when(tipService.getTipsForCreator(user)).thenReturn(List.of(tip));

        mockMvc.perform(get("/api/creator/tips")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(10.00))
                .andExpect(jsonPath("$[0].fromUserId").value("u_1***5"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getMyTips_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/tips")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyTips_Unauthorized_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/creator/tips")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}









package com.joinlivora.backend.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.JwtService;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorPayoutHoldController.class)
@Import(SecurityConfig.class)
class CreatorPayoutHoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayoutHoldService payoutHoldService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

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

        User creator = new User();
        creator.setEmail("creator@test.com");
        when(userService.getByEmail(anyString())).thenReturn(creator);
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void getStatus_AsCreator_ShouldReturnStatus() throws Exception {
        Instant unlockDate = Instant.now().plusSeconds(3600);
        PayoutHoldStatusDTO statusDTO = PayoutHoldStatusDTO.builder()
                .holdLevel(HoldLevel.MEDIUM)
                .unlockDate(unlockDate)
                .reason("Account under routine payout review")
                .build();

        when(payoutHoldService.getPayoutHoldStatus(any(User.class))).thenReturn(statusDTO);

        mockMvc.perform(get("/api/creator/payout-hold-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.reason").value("Account under routine payout review"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getStatus_AsRegularUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/payout-hold-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}









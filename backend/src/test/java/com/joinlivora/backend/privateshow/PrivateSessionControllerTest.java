package com.joinlivora.backend.privateshow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrivateSessionController.class)
@Import(SecurityConfig.class)
class PrivateSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PrivateSessionService sessionService;

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

    private User user;

    @BeforeEach
    void setUp() throws Exception {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
        when(userService.getByEmail(any())).thenReturn(user);

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

    private org.mockito.stubbing.Stubber doAnswer(org.mockito.stubbing.Answer<?> answer) {
        return org.mockito.Mockito.doAnswer(answer);
    }

    @Test
    @WithMockUser
    void requestPrivateShow_ShouldSucceed() throws Exception {
        PrivateSessionController.PrivateSessionRequestDto request = new PrivateSessionController.PrivateSessionRequestDto();
        request.setUserId(2L);
        request.setPricePerMinute(100);

        mockMvc.perform(post("/api/private-show/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(sessionService).requestPrivateShow(any(User.class), eq(2L), eq(100L));
    }

    @Test
    @WithMockUser
    void acceptRequest_ShouldSucceed() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/private-show/" + sessionId + "/accept")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(sessionService).acceptRequest(any(User.class), eq(sessionId));
    }

    @Test
    @WithMockUser
    void startSession_ShouldSucceed() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/private-show/" + sessionId + "/start")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(sessionService).startSession(any(User.class), eq(sessionId));
    }
}









package com.joinlivora.backend.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.payment.dto.StripeCheckoutRequest;
import com.joinlivora.backend.stripe.service.StripeCheckoutService;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeCheckoutController.class)
@Import(SecurityConfig.class)
class StripeCheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StripeCheckoutController stripeCheckoutController;

    @MockBean
    private StripeCheckoutService stripeCheckoutService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

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
        org.springframework.test.util.ReflectionTestUtils.setField(stripeCheckoutController, "stripeEnabled", true);
        
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
    @WithMockUser(roles = "USER")
    void health_ShouldReturnUp_WhenEnabledAndConnected() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(stripeCheckoutController, "stripeEnabled", true);
        when(stripeCheckoutService.checkHealth()).thenReturn(true);

        mockMvc.perform(get("/api/payments/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.stripeEnabled").value(true))
                .andExpect(jsonPath("$.stripeConnected").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void health_ShouldReturnDown_WhenDisabledOrDisconnected() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(stripeCheckoutController, "stripeEnabled", false);
        when(stripeCheckoutService.checkHealth()).thenReturn(false);

        mockMvc.perform(get("/api/payments/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.stripeEnabled").value(false))
                .andExpect(jsonPath("$.stripeConnected").value(false));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTipCheckoutSession_ShouldReturnUrl() throws Exception {
        User user = new User();
        user.setId(123L);
        user.setEmail("user@test.com");

        com.joinlivora.backend.creator.model.CreatorProfile creator = new com.joinlivora.backend.creator.model.CreatorProfile();
        creator.setDisplayName("John Doe");

        when(userService.getByEmail("user@test.com")).thenReturn(user);
        when(creatorProfileService.getCreatorByUserId(456L)).thenReturn(creator);
        when(creatorProfileService.getCreatorIdByUserId(456L)).thenReturn(java.util.Optional.of(456L));
        when(stripeCheckoutService.createCheckoutSession(eq(456L), eq("John Doe"), eq(123L), eq(1000L)))
                .thenReturn("https://checkout.stripe.com/test");

        StripeCheckoutRequest request = new StripeCheckoutRequest(456L, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/payments/tip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/test"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void createGenericCheckoutSession_AsCreator_ShouldReturnUrl() throws Exception {
        User user = new User();
        user.setId(789L);
        user.setEmail("creator@test.com");
        user.setDisplayName("Test User");

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(stripeCheckoutService.createCheckoutSession(eq(789L), eq("Test User"), eq(789L), eq(500L)))
                .thenReturn("https://checkout.stripe.com/generic");

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("amount", 5.00);

        mockMvc.perform(post("/api/payments/create-checkout-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/generic"));
    }

    @Test
    void createTipCheckoutSession_Unauthorized_ShouldReturnUnauthorized() throws Exception {
        StripeCheckoutRequest request = new StripeCheckoutRequest(456L, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/payments/tip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTipCheckoutSession_InvalidAmount_ShouldReturnBadRequest() throws Exception {
        StripeCheckoutRequest request = new StripeCheckoutRequest(456L, BigDecimal.ZERO);

        mockMvc.perform(post("/api/payments/tip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTipCheckoutSession_CreatorNotFound_ShouldReturnNotFound() throws Exception {
        when(creatorProfileService.getCreatorByUserId(456L))
                .thenThrow(new com.joinlivora.backend.exception.ResourceNotFoundException("Creator not found"));

        StripeCheckoutRequest request = new StripeCheckoutRequest(456L, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/payments/tip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}









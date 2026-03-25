package com.joinlivora.backend.token;

import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.wallet.UserWallet;
import com.joinlivora.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TokenController.class)
@org.springframework.context.annotation.Import(com.joinlivora.backend.config.SecurityConfig.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserService userService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private TokenPackageRepository tokenPackageRepository;

    @MockBean
    private TipOrchestrationService tipService;

    @MockBean
    private com.joinlivora.backend.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @BeforeEach
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
    @WithMockUser
    void getPackages_ShouldReturnList() throws Exception {
        TokenPackage pkg = TokenPackage.builder()
                .id(UUID.randomUUID())
                .name("100 Tokens")
                .tokenAmount(100)
                .build();
        when(tokenService.getActivePackages()).thenReturn(Collections.singletonList(pkg));

        mockMvc.perform(get("/api/tokens/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("100 Tokens"));
    }

    @Test
    @WithMockUser(username = "creator@test.com")
    void getBalance_ShouldReturnBalance() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(tokenService.getBalance(user)).thenReturn(UserWallet.builder().balance(500).build());

        mockMvc.perform(get("/api/tokens/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500));
    }

    @Test
    @WithMockUser(username = "creator@test.com")
    void createCheckoutSession_ShouldReturnUrl() throws Exception {
        UUID packageId = UUID.randomUUID();
        TokenPackage pkg = TokenPackage.builder()
                .id(packageId)
                .stripePriceId("price_123")
                .active(true)
                .build();
        User user = new User();
        user.setEmail("creator@test.com");

        when(tokenPackageRepository.findByActiveTrueAndId(packageId)).thenReturn(Optional.of(pkg));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(paymentService.createTokenCheckoutSession(eq(user), eq("price_123"), eq(packageId), any(), any(), any(), any()))
                .thenReturn("https://stripe.com/checkout");

        mockMvc.perform(post("/api/tokens/checkout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageId\": \"" + packageId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("https://stripe.com/checkout"));
    }
}









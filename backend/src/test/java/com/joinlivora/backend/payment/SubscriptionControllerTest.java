package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.SubscriptionPlanDTO;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.security.RefreshTokenService refreshTokenService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginSuccessHandler loginSuccessHandler;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @MockBean
    private com.joinlivora.backend.security.RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser
    void getPlans_ShouldReturnPlans() throws Exception {
        SubscriptionPlanDTO plan = SubscriptionPlanDTO.builder()
                .id("premium")
                .name("Premium")
                .price("9.99")
                .currency("EUR")
                .interval("month")
                .features(List.of("Feature 1"))
                .isPopular(true)
                .build();

        when(subscriptionService.getAvailablePlans()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/subscriptions/plans")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("premium"))
                .andExpect(jsonPath("$[0].name").value("Premium"))
                .andExpect(jsonPath("$[0].price").value("9.99"));
    }
}









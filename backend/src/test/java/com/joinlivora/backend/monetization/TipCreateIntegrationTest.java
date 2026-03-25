package com.joinlivora.backend.monetization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipCreateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StripeClient stripeClient;

    private User viewer;
    private User creator;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        viewer = TestUserFactory.createViewer("viewer@test.com");
        viewer = userRepository.save(viewer);

        creator = TestUserFactory.createCreator("creator@test.com");
        creator = userRepository.save(creator);

        // Mock Stripe PaymentIntent
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");
        when(mockIntent.getClientSecret()).thenReturn("pi_test_123_secret_abc");

        var piService = mock(com.stripe.service.PaymentIntentService.class);
        when(stripeClient.paymentIntents()).thenReturn(piService);
        when(piService.create(any(com.stripe.param.PaymentIntentCreateParams.class))).thenReturn(mockIntent);
    }

    @Test
    void createTestTip_ShouldReturnClientSecret() throws Exception {
        String token = jwtService.generateAccessToken(viewer);
        Map<String, Object> payload = Map.of(
                "creator", creator.getId(),
                "amount", 10.50
        );

        mockMvc.perform(post("/api/tips/create")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_test_123_secret_abc"));
    }

    @Test
    void createTestTip_Unauthenticated_ShouldReturn401() throws Exception {
        Map<String, Object> payload = Map.of(
                "creator", creator.getId(),
                "amount", 10.50
        );

        mockMvc.perform(post("/api/tips/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }
}









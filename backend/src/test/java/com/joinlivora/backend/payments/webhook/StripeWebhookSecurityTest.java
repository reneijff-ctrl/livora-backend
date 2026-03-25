package com.joinlivora.backend.payments.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StripeWebhookSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postStripeWebhook_IsPermitted_WithoutAuthAndWithoutCsrf() throws Exception {
        // We use a dummy payload and signature. It should reach the controller and return 400 Bad Request
        // because the signature is invalid, but NOT 401/403 from Spring Security.
        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "t=123,v1=123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"evt_test\"}"))
                .andExpect(status().isBadRequest()); 
    }

    @Test
    void getStripeWebhook_IsBlocked() throws Exception {
        mockMvc.perform(get("/webhooks/stripe"))
                .andExpect(status().isUnauthorized()); 
    }

    @Test
    void postOtherWebhook_IsBlocked() throws Exception {
        mockMvc.perform(post("/webhooks/other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden()); // Blocked by CSRF
    }
}









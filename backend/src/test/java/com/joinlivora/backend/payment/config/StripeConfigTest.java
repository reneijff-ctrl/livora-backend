package com.joinlivora.backend.payment.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class StripeConfigTest {

    @Test
    void stripeClient_ShouldInitializeStripeApiKey() {
        // Arrange
        StripeConfig config = new StripeConfig();
        String testApiKey = "sk_test_123";
        ReflectionTestUtils.setField(config, "stripeApiKey", testApiKey);
        
        // Reset Stripe.apiKey to ensure we are testing the initialization
        Stripe.apiKey = null;

        // Act
        StripeClient client = config.stripeClient();

        // Assert
        assertNotNull(client);
        assertEquals(testApiKey, Stripe.apiKey);
    }

    @Test
    void stripeClient_ShouldThrowException_WhenApiKeyIsMissing() {
        // Arrange
        StripeConfig config = new StripeConfig();
        ReflectionTestUtils.setField(config, "stripeApiKey", null);

        // Act & Assert
        assertThrows(IllegalStateException.class, config::stripeClient);
    }

    @Test
    void stripeClient_ShouldThrowException_WhenApiKeyIsEmpty() {
        // Arrange
        StripeConfig config = new StripeConfig();
        ReflectionTestUtils.setField(config, "stripeApiKey", "  ");

        // Act & Assert
        assertThrows(IllegalStateException.class, config::stripeClient);
    }

    @Test
    void validate_ShouldThrowException_WhenApiKeyDoesNotStartWithSk() {
        // Arrange
        StripeConfig config = new StripeConfig();
        ReflectionTestUtils.setField(config, "stripeApiKey", "pk_test_123");

        // Act & Assert
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void validate_ShouldThrowException_WhenApiKeyIsPlaceholder() {
        // Arrange
        StripeConfig config = new StripeConfig();
        ReflectionTestUtils.setField(config, "stripeApiKey", "${STRIPE_SECRET_KEY}");

        // Act & Assert
        assertThrows(IllegalStateException.class, config::validate);
    }
}









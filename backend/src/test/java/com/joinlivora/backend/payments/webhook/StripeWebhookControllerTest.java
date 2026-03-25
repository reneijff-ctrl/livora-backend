package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.payments.exception.WebhookReplayException;
import com.joinlivora.backend.payments.service.WebhookReplayProtectionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeWebhookService stripeWebhookService;

    @Mock
    private WebhookReplayProtectionService replayProtectionService;

    @InjectMocks
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "endpointSecret", "whsec_dev_fallback");
    }

    @Test
    void handleStripeWebhook_Success() throws Exception {
        // Given
        String payload = """
                {
                  "creator": "evt_123",
                  "object": "event",
                  "type": "other",
                  "api_version": "2023-10-16",
                  "data": {
                    "object": {
                      "creator": "pi_123",
                      "object": "payment_intent"
                    }
                  }
                }
                """;
        String sigHeader = "valid_sig";
        when(replayProtectionService.isReplay("evt_123")).thenReturn(false);

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody());
        verify(replayProtectionService).recordEvent(eq("evt_123"), eq("other"));
        verify(stripeWebhookService).processEvent(any(Event.class));
    }

    @Test
    void handleStripeWebhook_DisputeCreated_ShouldCallSpecificHandler() throws Exception {
        // Given
        String payload = """
                {
                  "creator": "evt_dispute_created",
                  "object": "event",
                  "type": "charge.dispute.created",
                  "api_version": "2023-10-16",
                  "data": {
                    "object": {
                      "creator": "dp_123",
                      "object": "dispute",
                      "charge": "ch_123",
                      "amount": 1000,
                      "currency": "usd",
                      "reason": "fraudulent"
                    }
                  }
                }
                """;
        String sigHeader = "any";
        when(replayProtectionService.isReplay("evt_dispute_created")).thenReturn(false);

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(stripeWebhookService).handleChargebackOpened(any(Dispute.class), eq("evt_dispute_created"));
    }

    @Test
    void handleStripeWebhook_DisputeClosed_ShouldCallSpecificHandler() throws Exception {
        // Given
        String payload = """
                {
                  "creator": "evt_dispute_closed",
                  "object": "event",
                  "type": "charge.dispute.closed",
                  "api_version": "2023-10-16",
                  "data": {
                    "object": {
                      "creator": "dp_456",
                      "object": "dispute",
                      "status": "lost"
                    }
                  }
                }
                """;
        String sigHeader = "any";
        when(replayProtectionService.isReplay("evt_dispute_closed")).thenReturn(false);

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(stripeWebhookService).handleChargebackClosed(any(Dispute.class), eq("evt_dispute_closed"));
    }

    @Test
    void handleStripeWebhook_SignatureFailure() throws Exception {
        // Given
        ReflectionTestUtils.setField(controller, "endpointSecret", "whsec_real");
        String payload = "{\"creator\": \"evt_123\"}";
        String sigHeader = "invalid_sig";

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid signature", response.getBody());
    }

    @Test
    void handleStripeWebhook_DuplicateEvent_FromController() throws Exception {
        // Given
        String payload = "{\"creator\": \"evt_123\"}";
        String sigHeader = "valid_sig";
        when(replayProtectionService.isReplay("evt_123")).thenReturn(true);

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Stripe event evt_123 has already been processed", response.getBody());
        verifyNoInteractions(stripeWebhookService);
    }

    @Test
    void handleStripeWebhook_InternalError() throws Exception {
        // Given
        String payload = """
                {
                  "creator": "evt_123",
                  "object": "event",
                  "type": "other",
                  "api_version": "2023-10-16",
                  "data": {
                    "object": {
                      "creator": "pi_123",
                      "object": "payment_intent"
                    }
                  }
                }
                """;
        String sigHeader = "valid_sig";
        when(replayProtectionService.isReplay("evt_123")).thenReturn(false);
        doThrow(new RuntimeException("DB error"))
                .when(stripeWebhookService).processEvent(any(Event.class));

        // When
        ResponseEntity<String> response = controller.handleStripeWebhook(payload, sigHeader);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Processing error", response.getBody());
    }
}









package com.joinlivora.backend.payment;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private StripeWebhookService stripeWebhookService;

    @InjectMocks
    private StripeWebhookController controller;

    private MockedStatic<Webhook> mockedWebhook;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "endpointSecret", "whsec_test_secret");
        ReflectionTestUtils.setField(controller, "stripeEnabled", true);
        mockedWebhook = mockStatic(Webhook.class);
    }

    @AfterEach
    void tearDown() {
        mockedWebhook.close();
    }

    @Test
    void handleStripeWebhook_ShouldPersistAndCallService() throws Exception {
        // Given
        String payload = "{\"id\": \"evt_123\", \"type\": \"invoice.payment_failed\"}";
        String sigHeader = "t=123,v1=456";
        
        Event event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
        mockedWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);
        
        when(webhookEventRepository.existsByStripeEventId("evt_123")).thenReturn(false);
        
        WebhookEvent savedEvent = new WebhookEvent();
        savedEvent.setId(UUID.randomUUID());
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(savedEvent);

        // When
        controller.handleStripeWebhook(payload, sigHeader);

        // Then
        verify(webhookEventRepository).save(any(WebhookEvent.class));
        verify(stripeWebhookService).processEventAsync(any(Event.class), any());
    }

    @Test
    void handleStripeWebhook_ShouldIgnoreDuplicate() throws Exception {
        // Given
        String payload = "{\"id\": \"evt_123\", \"type\": \"invoice.payment_failed\"}";
        String sigHeader = "t=123,v1=456";
        
        Event event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
        mockedWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);
        
        when(webhookEventRepository.existsByStripeEventId("evt_123")).thenReturn(true);

        // When
        controller.handleStripeWebhook(payload, sigHeader);

        // Then
        verify(webhookEventRepository, never()).save(any());
        verify(stripeWebhookService, never()).processEventAsync(any(), any());
    }
}









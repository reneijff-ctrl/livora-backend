package com.joinlivora.backend.payment;

import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookRetrySchedulerTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private StripeWebhookService stripeWebhookService;

    @InjectMocks
    private WebhookRetryScheduler scheduler;

    private static final String SAMPLE_PAYLOAD =
            "{\"id\":\"evt_test_123\",\"object\":\"event\",\"type\":\"invoice.payment_failed\"}";

    private WebhookEvent buildFailedEvent(int retryCount) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID());
        event.setStripeEventId("evt_test_123");
        event.setEventType("invoice.payment_failed");
        event.setPayload(SAMPLE_PAYLOAD);
        event.setProcessed(false);
        event.setErrorMessage("Transient DB error");
        event.setRetryCount(retryCount);
        return event;
    }

    @BeforeEach
    void setUp() {
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void retryFailedWebhookEvents_ShouldDoNothing_WhenNoFailedEvents() {
        when(webhookEventRepository.findFailedEventsForRetry(any(Instant.class), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.retryFailedWebhookEvents();

        verify(stripeWebhookService, never()).processEventAsync(any(), any());
    }

    @Test
    void retryFailedWebhookEvents_ShouldDispatchRetry_ForFailedEvent() {
        WebhookEvent failedEvent = buildFailedEvent(0);

        when(webhookEventRepository.findFailedEventsForRetry(any(Instant.class), anyInt(), any()))
                .thenReturn(List.of(failedEvent));

        scheduler.retryFailedWebhookEvents();

        // retryCount should be incremented and lastRetryAt set
        ArgumentCaptor<WebhookEvent> savedCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository, atLeastOnce()).save(savedCaptor.capture());
        WebhookEvent saved = savedCaptor.getAllValues().get(0);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastRetryAt()).isNotNull();

        // processEventAsync should be called with valid Event and the same internal ID
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(stripeWebhookService).processEventAsync(eventCaptor.capture(), idCaptor.capture());
        assertThat(eventCaptor.getValue().getId()).isEqualTo("evt_test_123");
        assertThat(idCaptor.getValue()).isEqualTo(failedEvent.getId());
    }

    @Test
    void retryEvent_ShouldSkip_WhenMaxRetriesReached() {
        WebhookEvent maxedEvent = buildFailedEvent(WebhookRetryScheduler.MAX_RETRIES);

        scheduler.retryEvent(maxedEvent);

        verify(stripeWebhookService, never()).processEventAsync(any(), any());
        // no save needed since we skip entirely
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void retryEvent_ShouldRecordError_WhenPayloadDeserializationFails() {
        WebhookEvent badPayloadEvent = buildFailedEvent(0);
        badPayloadEvent.setPayload("NOT_VALID_JSON{{{{");

        scheduler.retryEvent(badPayloadEvent);

        verify(stripeWebhookService, never()).processEventAsync(any(), any());
        // Should save twice: once for retry count increment, once to record deserialization error
        ArgumentCaptor<WebhookEvent> savedCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository, times(2)).save(savedCaptor.capture());
        WebhookEvent lastSaved = savedCaptor.getAllValues().get(1);
        assertThat(lastSaved.getErrorMessage()).contains("Payload deserialization failed");
    }

    @Test
    void retryFailedWebhookEvents_ShouldQueryWithCorrectBatchSize() {
        when(webhookEventRepository.findFailedEventsForRetry(any(Instant.class), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.retryFailedWebhookEvents();

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(webhookEventRepository).findFailedEventsForRetry(
                any(Instant.class),
                eq(WebhookRetryScheduler.MAX_RETRIES),
                pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(WebhookRetryScheduler.BATCH_SIZE);
    }

    @Test
    void retryFailedWebhookEvents_ShouldHandleMultipleFailedEvents() {
        WebhookEvent event1 = buildFailedEvent(1);
        WebhookEvent event2 = buildFailedEvent(2);

        when(webhookEventRepository.findFailedEventsForRetry(any(Instant.class), anyInt(), any()))
                .thenReturn(List.of(event1, event2));

        scheduler.retryFailedWebhookEvents();

        verify(stripeWebhookService, times(2)).processEventAsync(any(Event.class), any(UUID.class));
    }
}

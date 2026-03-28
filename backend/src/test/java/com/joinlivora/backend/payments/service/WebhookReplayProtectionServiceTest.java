package com.joinlivora.backend.payments.service;

import com.joinlivora.backend.payments.model.StripeWebhookEvent;
import com.joinlivora.backend.payments.repository.StripeWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookReplayProtectionServiceTest {

    @Mock
    private StripeWebhookEventRepository repository;

    @InjectMocks
    private WebhookReplayProtectionService service;

    @Test
    void tryClaimEvent_ShouldReturnTrue_WhenEventIsNew() {
        String eventId = "evt_123";
        String eventType = "payment_intent.succeeded";

        when(repository.saveAndFlush(any(StripeWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.tryClaimEvent(eventId, eventType);

        assertThat(result).isTrue();

        ArgumentCaptor<StripeWebhookEvent> captor = ArgumentCaptor.forClass(StripeWebhookEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        StripeWebhookEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getType()).isEqualTo(eventType);
        assertThat(saved.getStatus()).isEqualTo(StripeWebhookEvent.Status.PROCESSING);
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void tryClaimEvent_ShouldReturnFalse_WhenEventAlreadyExists() {
        String eventId = "evt_123";
        String eventType = "payment_intent.succeeded";

        when(repository.saveAndFlush(any(StripeWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        boolean result = service.tryClaimEvent(eventId, eventType);

        assertThat(result).isFalse();
    }

    @Test
    void tryClaimEvent_ShouldReturnFalse_WhenEventIdIsNull() {
        assertThat(service.tryClaimEvent(null, "type")).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void tryClaimEvent_ShouldReturnFalse_WhenEventIdIsBlank() {
        assertThat(service.tryClaimEvent("  ", "type")).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void markCompleted_ShouldUpdateStatusAndProcessedAt() {
        String eventId = "evt_123";
        StripeWebhookEvent event = StripeWebhookEvent.builder()
                .eventId(eventId)
                .type("payment_intent.succeeded")
                .status(StripeWebhookEvent.Status.PROCESSING)
                .build();

        when(repository.findById(eventId)).thenReturn(Optional.of(event));

        service.markCompleted(eventId);

        assertThat(event.getStatus()).isEqualTo(StripeWebhookEvent.Status.COMPLETED);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void markFailed_ShouldUpdateStatus() {
        String eventId = "evt_123";
        StripeWebhookEvent event = StripeWebhookEvent.builder()
                .eventId(eventId)
                .type("payment_intent.succeeded")
                .status(StripeWebhookEvent.Status.PROCESSING)
                .build();

        when(repository.findById(eventId)).thenReturn(Optional.of(event));

        service.markFailed(eventId);

        assertThat(event.getStatus()).isEqualTo(StripeWebhookEvent.Status.FAILED);
        verify(repository).save(event);
    }

    @Test
    void markCompleted_ShouldDoNothing_WhenEventNotFound() {
        when(repository.findById("evt_missing")).thenReturn(Optional.empty());

        service.markCompleted("evt_missing");

        verify(repository, never()).save(any());
    }

    @Test
    void markFailed_ShouldDoNothing_WhenEventNotFound() {
        when(repository.findById("evt_missing")).thenReturn(Optional.empty());

        service.markFailed("evt_missing");

        verify(repository, never()).save(any());
    }
}

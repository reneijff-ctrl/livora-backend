package com.joinlivora.backend.payments.service;

import com.joinlivora.backend.payments.exception.WebhookReplayException;
import com.joinlivora.backend.payments.model.StripeWebhookEvent;
import com.joinlivora.backend.payments.repository.StripeWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookReplayProtectionServiceTest {

    @Mock
    private StripeWebhookEventRepository repository;

    @InjectMocks
    private WebhookReplayProtectionService service;

    @Test
    void isReplay_ShouldReturnTrue_WhenEventExists() {
        String eventId = "evt_123";
        when(repository.existsByEventId(eventId)).thenReturn(true);

        boolean result = service.isReplay(eventId);

        assertThat(result).isTrue();
        verify(repository).existsByEventId(eventId);
    }

    @Test
    void isReplay_ShouldReturnFalse_WhenEventDoesNotExist() {
        String eventId = "evt_123";
        when(repository.existsByEventId(eventId)).thenReturn(false);

        boolean result = service.isReplay(eventId);

        assertThat(result).isFalse();
        verify(repository).existsByEventId(eventId);
    }

    @Test
    void isReplay_ShouldReturnFalse_WhenEventIdIsNull() {
        assertThat(service.isReplay(null)).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void recordEvent_ShouldSaveEvent_WhenNotDuplicate() {
        String eventId = "evt_123";
        String eventType = "payment_intent.succeeded";
        when(repository.existsByEventId(eventId)).thenReturn(false);

        service.recordEvent(eventId, eventType);

        verify(repository).save(any(StripeWebhookEvent.class));
    }

    @Test
    void recordEvent_ShouldThrowException_WhenDuplicate() {
        String eventId = "evt_123";
        String eventType = "payment_intent.succeeded";
        when(repository.existsByEventId(eventId)).thenReturn(true);

        assertThatThrownBy(() -> service.recordEvent(eventId, eventType))
                .isInstanceOf(WebhookReplayException.class)
                .hasMessageContaining(eventId);

        verify(repository, never()).save(any(StripeWebhookEvent.class));
    }

    @Test
    void recordEvent_ShouldNotSaveEvent_WhenEventIdIsNull() {
        service.recordEvent(null, "type");
        verifyNoInteractions(repository);
    }
}









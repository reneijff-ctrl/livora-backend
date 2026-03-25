package com.joinlivora.backend.payments.service;

import com.joinlivora.backend.payments.exception.WebhookReplayException;
import com.joinlivora.backend.payments.model.StripeWebhookEvent;
import com.joinlivora.backend.payments.repository.StripeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service responsible for preventing duplicate processing of Stripe webhook events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookReplayProtectionService {

    private final StripeWebhookEventRepository repository;

    /**
     * Checks if the given Stripe event ID has already been processed.
     *
     * @param eventId The Stripe event ID (e.g., evt_...)
     * @return true if the event is a duplicate, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isReplay(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return repository.existsByEventId(eventId);
    }

    /**
     * Records a Stripe event ID to prevent future replays.
     *
     * @param eventId   The Stripe event ID
     * @param eventType The type of the Stripe event
     */
    @Transactional
    public void recordEvent(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        if (repository.existsByEventId(eventId)) {
            throw new WebhookReplayException("Stripe event " + eventId + " has already been processed");
        }

        StripeWebhookEvent event = StripeWebhookEvent.builder()
                .eventId(eventId)
                .type(eventType)
                .receivedAt(Instant.now())
                .build();

        repository.save(event);
        log.info("Recorded Stripe event: {} ({})", eventId, eventType);
    }
}

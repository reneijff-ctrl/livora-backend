package com.joinlivora.backend.payments.service;

import com.joinlivora.backend.payments.model.StripeWebhookEvent;
import com.joinlivora.backend.payments.repository.StripeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service responsible for preventing duplicate processing of Stripe webhook events.
 * Uses atomic INSERT (unique constraint on eventId) to guarantee only one processor wins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookReplayProtectionService {

    private final StripeWebhookEventRepository repository;

    /**
     * Atomically claims a Stripe event for processing.
     * Uses INSERT with unique PK constraint — only one concurrent caller can succeed.
     *
     * @param eventId   The Stripe event ID (e.g., evt_...)
     * @param eventType The type of the Stripe event
     * @return true if successfully claimed, false if already exists (replay)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaimEvent(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        try {
            StripeWebhookEvent event = StripeWebhookEvent.builder()
                    .eventId(eventId)
                    .type(eventType)
                    .status(StripeWebhookEvent.Status.PROCESSING)
                    .receivedAt(Instant.now())
                    .build();

            repository.saveAndFlush(event);
            log.info("Claimed Stripe event for processing: {} ({})", eventId, eventType);
            return true;

        } catch (DataIntegrityViolationException e) {
            log.info("Stripe event already claimed (replay): {} ({})", eventId, eventType);
            return false;
        }
    }

    /**
     * Marks an event as successfully processed.
     *
     * @param eventId The Stripe event ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(StripeWebhookEvent.Status.COMPLETED);
            event.setProcessedAt(Instant.now());
            repository.save(event);
            log.info("Marked Stripe event as COMPLETED: {}", eventId);
        });
    }

    /**
     * Marks an event as failed. This allows future retry attempts
     * to reclaim events stuck in PROCESSING state.
     *
     * @param eventId The Stripe event ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(StripeWebhookEvent.Status.FAILED);
            repository.save(event);
            log.info("Marked Stripe event as FAILED: {}", eventId);
        });
    }
}

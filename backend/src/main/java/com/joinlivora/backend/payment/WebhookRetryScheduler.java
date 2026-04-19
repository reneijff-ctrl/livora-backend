package com.joinlivora.backend.payment;

import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryScheduler {

    static final int MAX_RETRIES = 5;
    static final int BATCH_SIZE = 20;
    static final int LOOKBACK_HOURS = 24;

    private final WebhookEventRepository webhookEventRepository;
    private final StripeWebhookService stripeWebhookService;

    @Scheduled(fixedDelay = 60_000)
    public void retryFailedWebhookEvents() {
        Instant since = Instant.now().minus(LOOKBACK_HOURS, ChronoUnit.HOURS);
        List<WebhookEvent> failedEvents = webhookEventRepository.findFailedEventsForRetry(
                since, MAX_RETRIES, PageRequest.of(0, BATCH_SIZE));

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("WEBHOOK_RETRY: Found {} failed webhook event(s) to retry", failedEvents.size());

        for (WebhookEvent webhookEvent : failedEvents) {
            retryEvent(webhookEvent);
        }
    }

    @Transactional
    public void retryEvent(WebhookEvent webhookEvent) {
        int attempt = webhookEvent.getRetryCount() + 1;

        if (webhookEvent.getRetryCount() >= MAX_RETRIES) {
            log.warn("WEBHOOK_RETRY: Skipping event {} ({}) — reached max retries ({})",
                    webhookEvent.getStripeEventId(), webhookEvent.getId(), MAX_RETRIES);
            return;
        }

        log.info("WEBHOOK_RETRY: Retrying event {} ({}) — attempt {}/{}",
                webhookEvent.getStripeEventId(), webhookEvent.getId(), attempt, MAX_RETRIES);

        // Increment retry tracking fields before dispatching so the counter is
        // persisted even if the async processing throws later.
        webhookEvent.setRetryCount(attempt);
        webhookEvent.setLastRetryAt(Instant.now());
        webhookEvent.setErrorMessage(null); // clear previous error so a new failure can be recorded
        webhookEventRepository.save(webhookEvent);

        // Re-parse the stored payload into a Stripe Event object.
        // ApiResource.GSON is the same Gson instance used internally by the SDK.
        Event event;
        try {
            event = ApiResource.GSON.fromJson(webhookEvent.getPayload(), Event.class);
        } catch (Exception e) {
            log.error("WEBHOOK_RETRY: Failed to deserialize payload for event {} ({}): {}",
                    webhookEvent.getStripeEventId(), webhookEvent.getId(), e.getMessage());
            webhookEvent.setErrorMessage("Payload deserialization failed: " + e.getMessage());
            webhookEventRepository.save(webhookEvent);
            return;
        }

        try {
            stripeWebhookService.processEventAsync(event, webhookEvent.getId());
            log.info("WEBHOOK_RETRY: Dispatched retry for event {} ({})",
                    webhookEvent.getStripeEventId(), webhookEvent.getId());
        } catch (Exception e) {
            log.error("WEBHOOK_RETRY: Failed to dispatch retry for event {} ({}): {}",
                    webhookEvent.getStripeEventId(), webhookEvent.getId(), e.getMessage());
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventRepository.save(webhookEvent);
        }
    }
}

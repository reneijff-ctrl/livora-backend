package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.payments.exception.WebhookReplayException;
import com.joinlivora.backend.payments.service.WebhookReplayProtectionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("paymentsStripeWebhookController")
@RequestMapping("/webhooks/stripe")
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;
    private final WebhookReplayProtectionService replayProtectionService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    public StripeWebhookController(
            @Qualifier("paymentsStripeWebhookService") StripeWebhookService stripeWebhookService,
            WebhookReplayProtectionService replayProtectionService) {
        this.stripeWebhookService = stripeWebhookService;
        this.replayProtectionService = replayProtectionService;
    }

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        // Hard guard: Disabling legacy controller in favor of /api/stripe/webhook
        if (true) {
            log.info("Stripe legacy webhook disabled: /webhooks/stripe short-circuited");
            return ResponseEntity.ok("Success");
        }
        if (!stripeEnabled) {
            log.info("Stripe disabled: /webhooks/stripe short-circuited");
            return ResponseEntity.ok("Stripe disabled");
        }
        log.info("SECURITY: Received Stripe webhook at /webhooks/stripe");
        
        try {
            Event event;
            if ("whsec_dev_fallback".equals(endpointSecret)) {
                event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
            } else {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            }

            if (event != null && event.getId() != null) {
                if (replayProtectionService.isReplay(event.getId())) {
                    log.warn("SECURITY: Duplicate Stripe webhook event detected in controller: {}", event.getId());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("Stripe event " + event.getId() + " has already been processed");
                }
                // Record event to prevent replay
                replayProtectionService.recordEvent(event.getId(), event.getType());
            }

            processEvent(event);
            return ResponseEntity.ok("Success");
        } catch (SignatureVerificationException e) {
            log.warn("SECURITY: Stripe webhook signature validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (WebhookReplayException e) {
            log.warn("SECURITY: Duplicate Stripe webhook event: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            log.error("SECURITY: Unexpected error during Stripe webhook processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }

    private void processEvent(Event event) {
        String eventType = event.getType();
        log.info("WEBHOOK: Routing Stripe event: {}", eventType);

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            log.warn("WEBHOOK: Could not deserialize event data for: {}", eventType);
            return;
        }

        switch (eventType) {
            case "charge.dispute.created":
                if (stripeObject instanceof Dispute dispute) {
                    handleChargebackOpened(dispute, event.getId());
                }
                break;
            case "charge.dispute.closed":
                if (stripeObject instanceof Dispute dispute) {
                    handleChargebackClosed(dispute, event.getId());
                }
                break;
            default:
                // Other events handled by service
                stripeWebhookService.processEvent(event);
                break;
        }
    }

    private void handleChargebackOpened(Dispute dispute, String eventId) {
        log.info("WEBHOOK: Handling chargeback opened for dispute: {}", dispute.getId());
        stripeWebhookService.handleChargebackOpened(dispute, eventId);
    }

    private void handleChargebackClosed(Dispute dispute, String eventId) {
        log.info("WEBHOOK: Handling chargeback closed for dispute: {}", dispute.getId());
        stripeWebhookService.handleChargebackClosed(dispute, eventId);
    }
}

package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.StripeVerificationResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController("paymentStripeWebhookController")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final WebhookEventRepository webhookEventRepository;
    private final StripeWebhookService stripeWebhookService;
    private final StripeClient stripeClient;
    private final PaymentRepository paymentRepository;
    private final UserService userService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    @PostMapping("/api/stripe/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        if (!stripeEnabled) {
            log.info("Stripe disabled: webhook short-circuited");
            return ResponseEntity.ok("Stripe disabled");
        }
        if (sigHeader == null || sigHeader.isEmpty()) {
            log.warn("SECURITY: Received webhook without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }
        
        if (endpointSecret == null || endpointSecret.trim().isEmpty()) {
            log.error("SECURITY: Stripe webhook secret is missing! Webhook processing aborted.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook secret misconfigured");
        }

        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SECURITY: Failed to read webhook request body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to read body");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.error("SECURITY: Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("SECURITY: Error constructing Stripe event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error processing event");
        }

        // 1. Idempotency handling: check if event has already been processed
        String stripeEventId = event.getId();
        if (webhookEventRepository.existsByStripeEventId(stripeEventId)) {
            log.info("SECURITY: Duplicate Stripe webhook event ignored: {}", stripeEventId);
            return ResponseEntity.ok("Already processed");
        }

        // 2. Persist Webhook Event
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setStripeEventId(stripeEventId);
        webhookEvent.setEventType(event.getType());
        webhookEvent.setPayload(payload);
        try {
            webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            log.info("SECURITY: Duplicate Stripe webhook event detected during save: {}", stripeEventId);
            return ResponseEntity.ok("Already processed");
        } catch (Exception e) {
            log.error("SECURITY: Failed to persist Stripe webhook event: {}", stripeEventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Database error");
        }

        // Handle the event asynchronously via Service (this will NOT block the response)
        stripeWebhookService.processEventAsync(event, webhookEvent.getId());

        return ResponseEntity.ok("Received");
    }

    @GetMapping("/api/payments/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StripeVerificationResponse> verifySession(@RequestParam("session_id") String sessionId) throws StripeException {
        if (!stripeEnabled) {
            log.info("Stripe disabled: verifySession short-circuited");
            return ResponseEntity.ok(StripeVerificationResponse.failed());
        }

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Ownership check: verify the session belongs to the authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.getByEmail(authentication.getName());

        Payment payment = paymentRepository.findByStripeSessionId(sessionId).orElse(null);
        if (payment == null) {
            // Session not found in our DB — do not reveal whether it exists; return 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!payment.getUser().getId().equals(currentUser.getId())) {
            // Session belongs to a different user — return 403 without leaking details
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Session session = stripeClient.checkout().sessions().retrieve(sessionId);

        if ("paid".equals(session.getPaymentStatus()) && "payment".equals(session.getMode())) {
            return ResponseEntity.ok(StripeVerificationResponse.paid(session.getAmountTotal()));
        }

        log.warn("Stripe session {} verify failed: status={}, mode={}",
                sessionId, session.getPaymentStatus(), session.getMode());
        return ResponseEntity.ok(StripeVerificationResponse.failed());
    }
}

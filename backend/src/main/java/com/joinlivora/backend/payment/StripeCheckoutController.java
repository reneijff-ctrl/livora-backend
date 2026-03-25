package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.PaymentHealthResponse;
import com.joinlivora.backend.payment.dto.StripeCheckoutRequest;
import com.joinlivora.backend.payment.dto.StripeCheckoutResponse;
import com.joinlivora.backend.stripe.service.StripeCheckoutService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class StripeCheckoutController {

    private final StripeCheckoutService stripeCheckoutService;
    private final UserService userService;
    private final com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    @GetMapping("/health")
    public ResponseEntity<PaymentHealthResponse> getHealth() {
        boolean connected = stripeCheckoutService.checkHealth();
        PaymentHealthResponse response = new PaymentHealthResponse(
                connected ? "UP" : "DOWN",
                stripeEnabled,
                connected
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tip")
    public ResponseEntity<StripeCheckoutResponse> createTipCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody StripeCheckoutRequest request
    ) throws Exception {
        if (!stripeEnabled) {
            log.info("Stripe disabled: returning placeholder checkout response for tip endpoint");
            return ResponseEntity.ok(new StripeCheckoutResponse(""));
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Long creatorUserId = request.getCreatorId();
        com.joinlivora.backend.creator.model.CreatorProfile profile;
        try {
            profile = creatorProfileService.getCreatorByUserId(creatorUserId);
        } catch (com.joinlivora.backend.exception.ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        Long internalCreatorId = creatorProfileService.getCreatorIdByUserId(creatorUserId)
                .orElse(profile.getId());
        
        User user = userService.getByEmail(userDetails.getUsername());
        
        // Convert amount (e.g. 10.50 EUR) to cents (1050)
        long amountCents = request.getAmount().multiply(new java.math.BigDecimal("100")).longValue();

        String checkoutUrl = stripeCheckoutService.createCheckoutSession(
                internalCreatorId,
                profile.getDisplayName(),
                user.getId(),
                amountCents
        );
        
        return ResponseEntity.ok(new StripeCheckoutResponse(checkoutUrl));
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<StripeCheckoutResponse> createGenericCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody java.util.Map<String, Object> payload
    ) throws Exception {
        if (!stripeEnabled) {
            log.info("Stripe disabled: returning placeholder checkout response for generic checkout endpoint");
            return ResponseEntity.ok(new StripeCheckoutResponse(""));
        }
        Object amountObj = payload.get("amount");
        if (amountObj == null) {
            return ResponseEntity.badRequest().build();
        }
        
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(amountObj.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.getByEmail(userDetails.getUsername());
        long amountCents = amount.multiply(new java.math.BigDecimal("100")).longValue();
        
        // For temporary generic session, use user's own ID as creator if none provided
        // This satisfies "no creator profile needed" requirement
        String checkoutUrl = stripeCheckoutService.createCheckoutSession(
                user.getId(),
                user.getDisplayName(),
                user.getId(),
                amountCents
        );
        
        return ResponseEntity.ok(new StripeCheckoutResponse(checkoutUrl));
    }
}

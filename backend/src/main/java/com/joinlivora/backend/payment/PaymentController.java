package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.CheckoutResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Checkout session requested for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        try {
            String checkoutUrl = paymentService.createCheckoutSession(user);
            return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
        } catch (StripeException e) {
            log.error("SECURITY: Failed to create Stripe checkout session for user: {}", user.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

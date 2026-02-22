package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @GetMapping("/plans")
    public ResponseEntity<java.util.List<com.joinlivora.backend.payment.dto.SubscriptionPlanDTO>> getPlans() {
        return ResponseEntity.ok(subscriptionService.getAvailablePlans());
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Fetching subscription for creator: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        SubscriptionResponse response = subscriptionService.getSubscriptionForUser(user);
        if (response != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSubscription(
            @AuthenticationPrincipal UserDetails userDetails
    ) throws com.stripe.exception.StripeException {
        log.info("SECURITY: Cancellation requested for creator: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        subscriptionService.cancelSubscription(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resume")
    public ResponseEntity<Void> resumeSubscription(
            @AuthenticationPrincipal UserDetails userDetails
    ) throws com.stripe.exception.StripeException {
        log.info("SECURITY: Resume requested for creator: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        subscriptionService.resumeSubscription(user);
        return ResponseEntity.ok().build();
    }
}

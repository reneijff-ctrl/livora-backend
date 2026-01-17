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

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Fetching subscription for user: {}", userDetails.getUsername());
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
    ) {
        log.info("SECURITY: Cancellation requested for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        try {
            subscriptionService.cancelSubscription(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("SECURITY: Failed to cancel subscription for user: {}", user.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/resume")
    public ResponseEntity<Void> resumeSubscription(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Resume requested for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        try {
            subscriptionService.resumeSubscription(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("SECURITY: Failed to resume subscription for user: {}", user.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

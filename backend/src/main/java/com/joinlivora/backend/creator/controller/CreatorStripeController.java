package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorStripeStatusResponse;
import com.joinlivora.backend.creator.service.CreatorStripeService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/creator/stripe")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR')")
public class CreatorStripeController {

    private final CreatorStripeService creatorStripeService;
    private final UserService userService;

    @PostMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createStripeAccount(@AuthenticationPrincipal UserPrincipal principal) throws com.stripe.exception.StripeException {
        log.info("STRIPE: Stripe account requested by creator: {}", principal.getEmail());
        User user = userService.getById(principal.getUserId());

        String stripeAccountId = creatorStripeService.createOrGetStripeAccount(user);
        String onboardingUrl = creatorStripeService.generateOnboardingLink(stripeAccountId);

        return ResponseEntity.ok(Map.of(
                "stripeAccountId", stripeAccountId,
                "onboardingUrl", onboardingUrl
        ));
    }

    @PostMapping("/onboard")
    public ResponseEntity<?> onboard(@AuthenticationPrincipal UserPrincipal principal) throws com.stripe.exception.StripeException {
        log.info("STRIPE: Onboarding link requested by creator: {}", principal.getEmail());
        User user = userService.getById(principal.getUserId());

        String stripeAccountId = creatorStripeService.createOrGetStripeAccount(user);
        String onboardingUrl = creatorStripeService.generateOnboardingLink(stripeAccountId);

        return ResponseEntity.ok(Map.of("onboardingUrl", onboardingUrl));
    }

    @GetMapping("/onboarding-link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getOnboardingLink(@AuthenticationPrincipal UserPrincipal principal) throws com.stripe.exception.StripeException {
        log.info("STRIPE: Onboarding link (GET) requested by creator: {}", principal.getEmail());
        User user = userService.getById(principal.getUserId());

        String stripeAccountId = user.getStripeAccountId();
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            log.warn("STRIPE: Onboarding link requested for user {} but no stripeAccountId exists", user.getEmail());
            return ResponseEntity.badRequest().body(Map.of("message", "Stripe account not found for this user"));
        }

        String onboardingUrl = creatorStripeService.generateOnboardingLink(stripeAccountId);
        return ResponseEntity.ok(Map.of("onboardingUrl", onboardingUrl));
    }

    @GetMapping("/status")
    public ResponseEntity<CreatorStripeStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) throws com.stripe.exception.StripeException {
        User user = userService.getById(principal.getUserId());
        CreatorStripeStatusResponse status = creatorStripeService.getStripeStatus(user);
        return ResponseEntity.ok(status);
    }
}

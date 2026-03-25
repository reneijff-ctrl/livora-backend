package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutEligibilityResponseDTO;
import com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorPayoutController {

    private final PayoutService payoutService;
    private final CreatorPayoutService creatorPayoutService;
    private final PayoutRequestService payoutRequestService;
    private final UserService userService;
    private final LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;
    private final StripeConnectService stripeConnectService;
    private final CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    @GetMapping
    public ResponseEntity<List<PayoutRequestResponseDTO>> getPayouts(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("CREATOR_PAYOUT: Payout list requested by: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        List<PayoutRequest> requests = payoutRequestService.getPayoutRequestsByUser(user);
        return ResponseEntity.ok(requests.stream()
                .map(payoutRequestService::mapToResponseDTO)
                .toList());
    }

    @PostMapping("/onboard")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<Map<String, String>> onboard(java.security.Principal principal) throws com.stripe.exception.StripeException {
        if (!stripeEnabled) {
            log.info("Stripe disabled: payout onboard short-circuited");
            return ResponseEntity.ok(Map.of("onboardingUrl", ""));
        }
        log.info("STRIPE: Onboarding link requested by creator: {}", principal.getName());
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));

        String stripeAccountId = stripeConnectService.createOrGetStripeAccount(user);
        log.info("PAYOUT_DEBUG: onboard stripeAccountId={} for user={}", stripeAccountId, user.getId());

        // Create CreatorPayoutSettings if missing
        UUID creatorId = new UUID(0L, user.getId());
        if (creatorPayoutSettingsRepository.findByCreatorId(creatorId).isEmpty()) {
            CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                    .creatorId(creatorId)
                    .stripeAccountId(stripeAccountId)
                    .payoutMethod(PayoutMethod.STRIPE_SEPA)
                    .minimumPayoutAmount(BigDecimal.valueOf(50.00))
                    .enabled(false)
                    .build();
            creatorPayoutSettingsRepository.save(settings);
            log.info("PAYOUT_DEBUG: created CreatorPayoutSettings for creatorId={}", creatorId);
        }

        String returnUrl = frontendUrl + "/creator/stripe/success";
        String refreshUrl = frontendUrl + "/creator/stripe/retry";

        String onboardingUrl = stripeConnectService.generateOnboardingLink(stripeAccountId, returnUrl, refreshUrl);

        return ResponseEntity.ok(Map.of("onboardingUrl", onboardingUrl));
    }


    @GetMapping("/history")
    public ResponseEntity<List<Payout>> getPayoutHistory(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed payout history for creator ID {}", userDetails.getUsername(), user.getId());
        return ResponseEntity.ok(payoutService.getPayoutHistory(user));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<PayoutEligibilityResponseDTO> checkEligibility(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} requested payout eligibility check", userDetails.getUsername());
        return ResponseEntity.ok(payoutRequestService.checkEligibility(user));
    }

    @PostMapping("/request")
    public ResponseEntity<PayoutRequestResponseDTO> requestPayout(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} requested a payout", userDetails.getUsername());
        PayoutRequest request = payoutRequestService.createPayoutRequest(user);
        return ResponseEntity.ok(payoutRequestService.mapToResponseDTO(request));
    }

    @GetMapping("/account")
    public ResponseEntity<?> getStripeAccountStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed Stripe account status for creator ID {}", userDetails.getUsername(), user.getId());
        return creatorStripeAccountRepository.findByCreatorId(user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

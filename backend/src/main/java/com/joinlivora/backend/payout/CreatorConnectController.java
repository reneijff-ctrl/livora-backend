package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/creator/connect")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorConnectController {

    private final CreatorConnectService creatorConnectService;
    private final UserService userService;
    private final StripeAccountRepository stripeAccountRepository;

    @PostMapping("/start")
    public ResponseEntity<?> startOnboarding(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("SECURITY: Creator onboarding started for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        try {
            String onboardingUrl = creatorConnectService.createOnboardingLink(user);
            return ResponseEntity.ok(Map.of("redirectUrl", onboardingUrl));
        } catch (Exception e) {
            log.error("SECURITY: Failed to start creator onboarding for user: {}", user.getEmail(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Failed to start onboarding"));
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return stripeAccountRepository.findByUser(user)
                .map(account -> ResponseEntity.ok(Map.of(
                        "onboardingCompleted", account.isOnboardingCompleted(),
                        "payoutsEnabled", account.isPayoutsEnabled(),
                        "stripeAccountId", account.getStripeAccountId()
                )))
                .orElse(ResponseEntity.ok(Map.of("onboardingCompleted", false)));
    }
}

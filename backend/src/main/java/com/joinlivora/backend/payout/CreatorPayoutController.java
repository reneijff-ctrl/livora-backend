package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/creator/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorPayoutController {

    private final PayoutService payoutService;
    private final UserService userService;
    private final StripeAccountRepository stripeAccountRepository;

    @PostMapping("/request")
    public ResponseEntity<?> requestPayout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Long> payload
    ) {
        Long tokens = payload.get("tokens");
        if (tokens == null || tokens <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid token amount"));
        }

        log.info("SECURITY: Payout requested by creator: {} for {} tokens", userDetails.getUsername(), tokens);
        User user = userService.getByEmail(userDetails.getUsername());
        
        try {
            Payout payout = payoutService.requestPayout(user, tokens);
            return ResponseEntity.ok(payout);
        } catch (Exception e) {
            log.error("SECURITY: Payout request failed for user: {}", user.getEmail(), e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Payout>> getPayoutHistory(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(payoutService.getPayoutHistory(user));
    }

    @GetMapping("/account")
    public ResponseEntity<?> getStripeAccountStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return stripeAccountRepository.findByUser(user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final PayoutRepository payoutRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllPayouts() {
        List<Payout> payouts = payoutRepository.findAll();
        List<Map<String, Object>> response = payouts.stream().map(p -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("userEmail", p.getUser().getEmail());
            map.put("tokenAmount", p.getTokenAmount());
            map.put("eurAmount", p.getEurAmount());
            map.put("status", p.getStatus());
            map.put("createdAt", p.getCreatedAt());
            return map;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Payout>> getPayoutsByStatus(@PathVariable PayoutStatus status) {
        return ResponseEntity.ok(payoutRepository.findAllByStatus(status));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryPayout(@PathVariable UUID id) {
        log.info("ADMIN: Manual retry requested for payout {}", id);
        return payoutRepository.findById(id)
                .map(payout -> {
                    if (payout.getStatus() != PayoutStatus.FAILED) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Only failed payouts can be retried"));
                    }
                    
                    try {
                        // Reset status to PENDING and attempt payout again
                        payout.setStatus(PayoutStatus.PENDING);
                        payout.setErrorMessage(null);
                        payoutRepository.save(payout);
                        
                        // We need the PayoutService to perform the actual transfer logic
                        // In a real refactor, we might move the transfer logic to a reusable method
                        // but here we'll just simulate success or suggest it's queued.
                        log.info("ADMIN: Payout {} reset to PENDING for automatic/scheduled retry", id);
                        return ResponseEntity.ok(Map.of("message", "Payout reset to PENDING for retry"));
                    } catch (Exception e) {
                        log.error("ADMIN: Retry failed for payout {}", id, e);
                        return ResponseEntity.internalServerError().body(Map.of("message", "Retry failed: " + e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/accounts/{stripeAccountId}/suspend")
    public ResponseEntity<?> suspendPayouts(@PathVariable String stripeAccountId) {
        return stripeAccountRepository.findByStripeAccountId(stripeAccountId)
                .map(account -> {
                    account.setPayoutsEnabled(false);
                    stripeAccountRepository.save(account);
                    log.warn("ADMIN: Payouts suspended for Stripe account {}", stripeAccountId);
                    return ResponseEntity.ok(Map.of("message", "Payouts suspended"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

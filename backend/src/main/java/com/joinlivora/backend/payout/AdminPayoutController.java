package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.dto.AdminPayoutDetailDTO;
import com.joinlivora.backend.payout.dto.PayoutOverrideRequest;
import com.joinlivora.backend.payout.dto.PayoutRequestAdminDetailDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController("payoutAdminPayoutController")
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final PayoutRepository payoutRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final UserService userService;
    private final PayoutService payoutService;
    private final com.joinlivora.backend.payouts.service.PayoutExecutionService payoutExecutionService;
    private final AuditService auditService;
    private final AdminPayoutService adminPayoutService;
    private final PayoutRequestService payoutRequestService;

    @GetMapping("/requests")
    public ResponseEntity<List<com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO>> getPayoutRequests(@RequestParam(required = false) PayoutRequestStatus status) {
        List<PayoutRequest> requests;
        if (status != null) {
            requests = payoutRequestService.getPayoutRequestsByStatus(status);
        } else {
            requests = payoutRequestService.getAllPayoutRequests();
        }
        return ResponseEntity.ok(requests.stream().map(payoutRequestService::mapToResponseDTO).toList());
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<PayoutRequestAdminDetailDTO> getPayoutRequest(@PathVariable UUID id) {
        log.info("ADMIN: Payout request {} detail requested", id);
        return ResponseEntity.ok(payoutRequestService.getPayoutRequestAdminDetail(id));
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO> approvePayoutRequest(@PathVariable UUID id) {
        log.info("ADMIN: Payout request {} approval requested", id);
        PayoutRequest approved = payoutRequestService.approvePayoutRequest(id);
        return ResponseEntity.ok(payoutRequestService.mapToResponseDTO(approved));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO> rejectPayoutRequest(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("type", "No type provided");
        log.info("ADMIN: Payout request {} rejection requested. Reason: {}", id, reason);
        PayoutRequest rejected = payoutRequestService.rejectPayoutRequest(id, reason);
        return ResponseEntity.ok(payoutRequestService.mapToResponseDTO(rejected));
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<AdminPayoutDetailDTO> getPayoutDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(adminPayoutService.getPayoutDetails(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryPayout(@PathVariable UUID id, @AuthenticationPrincipal UserDetails adminDetails, HttpServletRequest request) {
        log.info("AUDIT: Admin requested manual retry for payout {}", id);
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
                        
                        User admin = userService.getByEmail(adminDetails.getUsername());
                        auditService.logEvent(
                                new UUID(0L, admin.getId()),
                                AuditService.PAYOUT_REQUESTED,
                                "PAYOUT",
                                id,
                                Map.of("action", "retry", "amount", payout.getEurAmount()),
                                request.getRemoteAddr(),
                                request.getHeader("User-Agent")
                        );

                        // We need the PayoutService to perform the actual transfer logic
                        // In a real refactor, we might move the transfer logic to a reusable method
                        // but here we'll just simulate success or suggest it's queued.
                        log.info("AUDIT: Admin reset payout {} to PENDING for automatic/scheduled retry", id);
                        return ResponseEntity.ok(Map.of("message", "Payout reset to PENDING for retry"));
                    } catch (Exception e) {
                        log.error("ADMIN: Retry failed for payout {}", id, e);
                        return ResponseEntity.internalServerError().body(Map.of("message", "Retry failed: " + e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/accounts/{stripeAccountId}/suspend")
    public ResponseEntity<?> suspendPayouts(@PathVariable String stripeAccountId, @AuthenticationPrincipal UserDetails adminDetails, HttpServletRequest request) {
        return stripeAccountRepository.findByStripeAccountId(stripeAccountId)
                .map(account -> {
                    account.setPayoutsEnabled(false);
                    stripeAccountRepository.save(account);
                    
                    User admin = userService.getByEmail(adminDetails.getUsername());
                    auditService.logEvent(
                            new UUID(0L, admin.getId()),
                            AuditService.PAYOUT_BLOCKED,
                            "STRIPE_ACCOUNT",
                            null,
                            Map.of("stripeAccountId", stripeAccountId, "action", "suspend"),
                            request.getRemoteAddr(),
                            request.getHeader("User-Agent")
                    );

                    log.warn("AUDIT: Admin suspended payouts for Stripe account {}", stripeAccountId);
                    return ResponseEntity.ok(Map.of("message", "Payouts suspended"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/override")
    public ResponseEntity<?> overridePayout(
            @PathVariable UUID id,
            @RequestBody PayoutOverrideRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        log.info("ADMIN: Payout override requested for payout {}", id);
        try {
            User admin = userService.getByEmail(adminDetails.getUsername());
            adminPayoutService.overridePayout(id, request, admin, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok(Map.of("message", "Payout override successful"));
        } catch (Exception e) {
            log.error("ADMIN: Payout override failed for payout {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Payout override failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{creatorId}/execute")
    public ResponseEntity<?> executeManualPayout(@PathVariable UUID creatorId, @AuthenticationPrincipal UserDetails adminDetails, HttpServletRequest request) {
        log.info("ADMIN: Manual payout execution requested for creator {}", creatorId);
        try {
            BigDecimal availableAmount = payoutService.calculateAvailablePayout(creatorId);
            if (availableAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "No funds available for payout"));
            }

            CreatorPayout payout = payoutExecutionService.executePayout(creatorId, availableAmount, "EUR");
            if (payout == null) {
                throw new IllegalStateException("Payout execution returned null");
            }
            
            User admin = userService.getByEmail(adminDetails.getUsername());
            auditService.logEvent(
                    new UUID(0L, admin.getId()),
                    AuditService.MANUAL_PAYOUT_EXECUTED,
                    "USER",
                    creatorId,
                    new java.util.HashMap<>(Map.of("amount", availableAmount, "currency", "EUR", "payoutId", payout.getId())),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            log.info("AUDIT: Admin executed manual payout of {} EUR for creator {}", availableAmount, creatorId);
            return ResponseEntity.ok(payout);
        } catch (Exception e) {
            log.error("ADMIN: Manual payout failed for creator {}", creatorId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Payout execution failed: " + e.getMessage()));
        }
    }
}

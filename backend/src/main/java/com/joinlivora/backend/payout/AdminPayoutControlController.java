package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimitRequest;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutControlController {

    private final CreatorPayoutStateRepository creatorPayoutStateRepository;
    private final PayoutPolicyAuditService payoutPolicyAuditService;
    private final AuditService auditService;
    private final UserService userService;

    @GetMapping("/state/{creatorId}")
    public ResponseEntity<CreatorPayoutState> getPayoutState(@PathVariable UUID creatorId) {
        return creatorPayoutStateRepository.findByCreatorId(creatorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/limit/{creatorId}")
    public ResponseEntity<CreatorPayoutState> setLimit(
            @PathVariable UUID creatorId,
            @RequestBody PayoutLimitRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        log.info("ADMIN: Setting manual payout limit for creator {}: {} @ {}", 
                creatorId, request.getMaxPayoutAmount(), request.getFrequency());

        CreatorPayoutState state = creatorPayoutStateRepository.findByCreatorId(creatorId)
                .orElse(CreatorPayoutState.builder()
                        .creatorId(creatorId)
                        .build());

        state.setCurrentLimit(request.getMaxPayoutAmount());
        state.setFrequency(request.getFrequency());
        state.setStatus(mapToPayoutStateStatus(request.getFrequency()));
        state.setManualOverride(true);

        CreatorPayoutState saved = creatorPayoutStateRepository.save(state);
        payoutPolicyAuditService.logAdminDecision(creatorId, request.getMaxPayoutAmount(), request.getFrequency(), "Manual limit set by admin");
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "PAYOUT_LIMIT_SET",
                "USER",
                creatorId,
                Map.of("limitAmount", request.getMaxPayoutAmount() != null ? request.getMaxPayoutAmount() : "UNLIMITED", 
                       "frequency", request.getFrequency()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/unpause/{creatorId}")
    public ResponseEntity<CreatorPayoutState> unpause(
            @PathVariable UUID creatorId,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        log.info("ADMIN: Unpausing payouts for creator {}", creatorId);

        CreatorPayoutState state = creatorPayoutStateRepository.findByCreatorId(creatorId)
                .orElse(CreatorPayoutState.builder()
                        .creatorId(creatorId)
                        .build());

        state.setCurrentLimit(null);
        state.setFrequency(PayoutFrequency.NO_LIMIT);
        state.setStatus(PayoutStateStatus.ACTIVE);
        state.setManualOverride(true);

        CreatorPayoutState saved = creatorPayoutStateRepository.save(state);
        payoutPolicyAuditService.logAdminDecision(creatorId, null, PayoutFrequency.NO_LIMIT, "Manual unpause by admin");
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "PAYOUT_UNPAUSED",
                "USER",
                creatorId,
                Map.of("action", "unpause"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok(saved);
    }

    private PayoutStateStatus mapToPayoutStateStatus(PayoutFrequency frequency) {
        if (frequency == null) return PayoutStateStatus.ACTIVE;
        return switch (frequency) {
            case NO_LIMIT -> PayoutStateStatus.ACTIVE;
            case DAILY, WEEKLY -> PayoutStateStatus.LIMITED;
            case PAUSED -> PayoutStateStatus.PAUSED;
        };
    }
}

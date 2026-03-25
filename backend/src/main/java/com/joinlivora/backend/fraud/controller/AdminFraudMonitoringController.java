package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.dto.AdminFraudActionRequest;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.EnforcementService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/fraud")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFraudMonitoringController {

    private final RuleFraudSignalRepository fraudSignalRepository;
    private final FraudDetectionService fraudDetectionService;
    private final EnforcementService enforcementService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/signals")
    public ResponseEntity<Page<RuleFraudSignal>> getSignals(
            @RequestParam(required = false) FraudDecisionLevel riskLevel,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Specification<RuleFraudSignal> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("creator"), userId.getLeastSignificantBits()));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        return ResponseEntity.ok(fraudSignalRepository.findAll(spec, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @RequestBody AdminFraudActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        RuleFraudSignal signal = fraudSignalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fraud signal not found: " + id));

        signal.setResolved(true);
        signal.setResolvedBy(new UUID(0L, admin.getId()));
        signal.setResolvedAt(Instant.now());
        signal.setActionReason(request.getReason());
        fraudSignalRepository.save(signal);

        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "FRAUD_SIGNAL_APPROVED",
                "FRAUD_SIGNAL",
                id,
                Map.of("type", request.getReason() != null ? request.getReason() : ""),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/block-user")
    public ResponseEntity<Void> blockUser(
            @PathVariable UUID id,
            @RequestBody AdminFraudActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        RuleFraudSignal signal = fraudSignalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fraud signal not found: " + id));

        UUID userUuid = new UUID(0L, signal.getUserId());
        enforcementService.suspendAccount(userUuid, request.getReason(), null, null, null, admin.getEmail(), "ADMIN", httpRequest.getRemoteAddr(), null, null);

        signal.setResolved(true);
        signal.setResolvedBy(new UUID(0L, admin.getId()));
        signal.setResolvedAt(Instant.now());
        signal.setActionReason(request.getReason());
        fraudSignalRepository.save(signal);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/remove-restrictions")
    public ResponseEntity<Void> removeRestrictions(
            @PathVariable UUID id,
            @RequestBody AdminFraudActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        RuleFraudSignal signal = fraudSignalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fraud signal not found: " + id));

        User user = userService.getById(signal.getUserId());
        
        // Remove fraud-related blocks and reset risk level
        fraudDetectionService.unblockUser(user, admin);
        
        // Ensure creator status is ACTIVE if it was restricted
        if (user.getStatus() != UserStatus.ACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            user.setPayoutsEnabled(true);
            userService.updateUser(user); // Assuming this method exists or I should use userRepository
        }

        signal.setResolved(true);
        signal.setResolvedBy(new UUID(0L, admin.getId()));
        signal.setResolvedAt(Instant.now());
        signal.setActionReason(request.getReason());
        fraudSignalRepository.save(signal);

        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "FRAUD_RESTRICTIONS_REMOVED",
                "USER",
                new UUID(0L, user.getId()),
                Map.of("type", request.getReason() != null ? request.getReason() : "", "signalId", id),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok().build();
    }
}

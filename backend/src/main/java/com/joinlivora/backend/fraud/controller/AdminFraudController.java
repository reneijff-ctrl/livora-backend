package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.dto.*;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.AdminFraudQueryService;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.fraud.service.EnforcementService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/fraud")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminFraudController {

    private final RuleFraudSignalRepository fraudSignalRepository;
    private final FraudDetectionService fraudDetectionService;
    private final UserService userService;
    private final AdminFraudQueryService adminFraudQueryService;
    private final EnforcementService enforcementService;
    private final AuditService auditService;
    private final ChargebackService chargebackService;

    @GetMapping("/signals")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<FraudSignalResponseDTO>> getAllSignals(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) FraudDecisionLevel riskLevel,
            @RequestParam(required = false) FraudSignalType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Specification<RuleFraudSignal> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (resolved != null) {
                predicates.add(cb.equal(root.get("resolved"), resolved));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RuleFraudSignal> signals = fraudSignalRepository.findAll(spec, pageable);
        return ResponseEntity.ok(signals.map(this::mapToDTO));
    }

    private FraudSignalResponseDTO mapToDTO(RuleFraudSignal signal) {
        String userEmail = "Unknown";
        try {
            userEmail = userService.getById(signal.getUserId()).getEmail();
        } catch (Exception ignored) {}

        String creatorEmail = null;
        if (signal.getCreatorId() != null) {
            try {
                creatorEmail = userService.getById(signal.getCreatorId()).getEmail();
            } catch (Exception ignored) {}
        }

        return FraudSignalResponseDTO.builder()
                .id(signal.getId())
                .userId(signal.getUserId())
                .userEmail(userEmail)
                .creatorId(signal.getCreatorId())
                .creatorEmail(creatorEmail)
                .riskLevel(signal.getRiskLevel())
                .type(signal.getType())
                .reason(signal.getReason())
                .score(signal.getScore())
                .createdAt(signal.getCreatedAt())
                .resolved(signal.isResolved())
                .build();
    }

    @GetMapping("/signals/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<FraudSignalResponseDTO>> getSignalsByUserId(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<RuleFraudSignal> signals = fraudSignalRepository.findAllByUserId(userId.getLeastSignificantBits(), pageable);
        return ResponseEntity.ok(signals.map(this::mapToDTO));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserAdminResponseDTO>> getUsersByFraudRiskLevel(
            @RequestParam FraudRiskLevel riskLevel,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("ADMIN: Fetching users with fraud risk level: {}", riskLevel);
        return ResponseEntity.ok(adminFraudQueryService.getUsersByFraudRiskLevel(riskLevel, pageable));
    }

    @GetMapping("/failed-logins")
    public ResponseEntity<Page<FailedLoginDTO>> getFailedLogins(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("ADMIN: Fetching failed login attempts");
        return ResponseEntity.ok(adminFraudQueryService.getFailedLogins(pageable));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<Page<PaymentAnomalyDTO>> getPaymentAnomalies(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("ADMIN: Fetching payment anomalies");
        return ResponseEntity.ok(adminFraudQueryService.getPaymentAnomalies(pageable));
    }

    @GetMapping("/chargebacks")
    public ResponseEntity<Page<ChargebackAdminResponseDTO>> getChargebackHistory(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("ADMIN: Fetching chargeback history");
        return ResponseEntity.ok(chargebackService.getFraudChargebacks(pageable));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<FraudDashboardMetricsDTO> getDashboardMetrics() {
        log.info("ADMIN: Fetching fraud dashboard metrics");
        return ResponseEntity.ok(adminFraudQueryService.getFraudDashboardMetrics());
    }

    @PostMapping("/signals/{id}/resolve")
    public ResponseEntity<Void> resolveSignal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(userDetails.getUsername());
        fraudDetectionService.resolveSignal(id, admin);
        
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "FRAUD_SIGNAL_RESOLVED",
                "FRAUD_SIGNAL",
                id,
                Map.of("action", "resolve"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/fraud-risk")
    public ResponseEntity<Void> overrideFraudRisk(
            @PathVariable UUID userId,
            @RequestBody FraudRiskOverrideRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        User user = userService.getById(userId.getLeastSignificantBits());
        User admin = userService.getByEmail(userDetails.getUsername());
        fraudDetectionService.overrideRiskLevel(user, request.getRiskLevel(), admin);
        
        enforcementService.recordManualOverride(userId, "Risk level override to " + request.getRiskLevel(), "ADMIN", "INTERNAL", httpRequest.getRemoteAddr());
        
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.ROLE_CHANGED,
                "USER",
                userId,
                Map.of("action", "override_fraud_risk", "newRiskLevel", request.getRiskLevel()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<Void> unblockUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        User user = userService.getById(userId.getLeastSignificantBits());
        User admin = userService.getByEmail(userDetails.getUsername());
        fraudDetectionService.unblockUser(user, admin);
        
        enforcementService.recordManualOverride(userId, "Manual unblock", "ADMIN", "INTERNAL", httpRequest.getRemoteAddr());
        
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "ACCOUNT_UNBLOCKED",
                "USER",
                userId,
                Map.of("action", "unblock"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/events")
    public ResponseEntity<List<com.joinlivora.backend.fraud.model.FraudEvent>> getFraudHistory(@PathVariable UUID userId) {
        log.info("ADMIN: Fetching fraud history for creator: {}", userId);
        return ResponseEntity.ok(adminFraudQueryService.getFraudHistory(userId));
    }

    @GetMapping("/active-actions")
    public ResponseEntity<List<com.joinlivora.backend.fraud.model.FraudScore>> getUsersWithEnforcement() {
        log.info("ADMIN: Fetching all users with active enforcement actions");
        return ResponseEntity.ok(adminFraudQueryService.getUsersWithEnforcement());
    }

    @GetMapping("/users/{userId}/risk-score")
    public ResponseEntity<com.joinlivora.backend.fraud.model.RiskScore> getRiskScore(@PathVariable UUID userId) {
        log.info("ADMIN: Fetching risk score for creator: {}", userId);
        return adminFraudQueryService.getRiskScore(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

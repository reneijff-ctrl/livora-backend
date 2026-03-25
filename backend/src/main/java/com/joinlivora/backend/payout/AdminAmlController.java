package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.dto.AmlOverrideRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/aml")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAmlController {

    private final PayoutRiskRepository payoutRiskRepository;
    private final PayoutAbuseDetectionService payoutAbuseDetectionService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/risks")
    public ResponseEntity<Page<PayoutRisk>> getAllRisks(
            @PageableDefault(size = 20, sort = "lastEvaluatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(payoutRiskRepository.findAll(pageable));
    }

    @GetMapping("/risks/{userId}")
    public ResponseEntity<Page<PayoutRisk>> getRisksByUserId(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "lastEvaluatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(payoutRiskRepository.findAllByUserIdOrderByLastEvaluatedAtDesc(
                userId.getLeastSignificantBits(), pageable));
    }

    @PostMapping("/override/{userId}")
    public ResponseEntity<Void> overrideRisk(
            @PathVariable UUID userId,
            @RequestBody AmlOverrideRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User user = userService.getById(userId.getLeastSignificantBits());
        User admin = userService.getByEmail(adminDetails.getUsername());
        
        payoutAbuseDetectionService.override(user, request.getRiskScore(), request.getReason(), admin);
        
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "AML_RISK_OVERRIDE",
                "USER",
                userId,
                Map.of("riskScore", request.getRiskScore(), "type", request.getReason() != null ? request.getReason() : ""),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.noContent().build();
    }
}

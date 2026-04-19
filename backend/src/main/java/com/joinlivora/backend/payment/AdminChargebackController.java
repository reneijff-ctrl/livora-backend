package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin REST controller for chargeback management.
 * All reads are sourced from chargeback_cases (canonical table) and mapped
 * to the legacy Chargeback response type for API compatibility.
 */
@RestController("paymentAdminChargebackController")
@RequestMapping("/api/admin/chargebacks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChargebackController {

    private final ChargebackService chargebackService;

    @GetMapping
    public ResponseEntity<Page<Chargeback>> getAllChargebacks(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<Chargeback> page = chargebackService.getAllChargebackCasesPaged(pageable)
                .map(this::mapCaseToLegacy);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Chargeback> getChargebackById(@PathVariable UUID id) {
        return chargebackService.getChargebackById(id)
                .map(this::mapCaseToLegacy)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/correlated/{userId}")
    public ResponseEntity<List<Chargeback>> getCorrelatedChargebacks(@PathVariable UUID userId) {
        List<Chargeback> result = chargebackService
                .findCorrelatedCasesByUserId(userId.getLeastSignificantBits())
                .stream()
                .map(this::mapCaseToLegacy)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------------------
    // Mapping: ChargebackCase (chargeback_cases) → Chargeback (response shape)
    // Fields absent in ChargebackCase that existed in legacy_chargebacks are set
    // to null/false to preserve the response contract without breaking clients.
    // ---------------------------------------------------------------------------
    private Chargeback mapCaseToLegacy(ChargebackCase c) {
        return Chargeback.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .transactionId(null)           // not stored in chargeback_cases
                .creatorId(null)               // not stored in chargeback_cases
                .stripeChargeId(null)          // not stored in chargeback_cases
                .stripeDisputeId(null)         // not stored in chargeback_cases
                .reason(c.getReason())
                .amount(c.getAmount())
                .currency(c.getCurrency())
                .status(mapCaseStatus(c.getStatus()))
                .ipAddress(null)               // not stored in chargeback_cases
                .deviceFingerprint(null)
                .paymentMethodFingerprint(null)
                .paymentMethodBrand(null)
                .paymentMethodLast4(null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .resolved(c.getStatus() == com.joinlivora.backend.chargeback.model.ChargebackStatus.WON
                        || c.getStatus() == com.joinlivora.backend.chargeback.model.ChargebackStatus.LOST)
                .build();
    }

    private ChargebackStatus mapCaseStatus(com.joinlivora.backend.chargeback.model.ChargebackStatus s) {
        return switch (s) {
            case OPEN -> ChargebackStatus.RECEIVED;
            case UNDER_REVIEW -> ChargebackStatus.UNDER_REVIEW;
            case WON -> ChargebackStatus.WON;
            case LOST -> ChargebackStatus.LOST;
        };
    }
}

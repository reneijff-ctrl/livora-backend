package com.joinlivora.backend.chargeback.controller;

import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Internal controller for administrative management of chargebacks.
 */
@RestController
@RequestMapping("/internal/chargebacks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChargebackAdminController {

    private final ChargebackService chargebackService;

    @GetMapping
    public ResponseEntity<List<ChargebackCase>> getAllChargebacks() {
        return ResponseEntity.ok(chargebackService.getAllChargebackCases());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChargebackCase> getChargeback(@PathVariable UUID id) {
        return chargebackService.getChargebackById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ChargebackCase> updateStatus(
            @PathVariable UUID id,
            @RequestParam ChargebackStatus status
    ) {
        return ResponseEntity.ok(chargebackService.updateStatus(id, status));
    }
}

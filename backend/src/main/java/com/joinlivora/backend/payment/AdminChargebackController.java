package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.ChargebackService;
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
        return ResponseEntity.ok(chargebackService.getAllLegacyChargebacks(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Chargeback> getChargebackById(@PathVariable UUID id) {
        return chargebackService.getLegacyChargebackById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/correlated/{userId}")
    public ResponseEntity<List<Chargeback>> getCorrelatedChargebacks(@PathVariable UUID userId) {
        return ResponseEntity.ok(chargebackService.findCorrelatedChargebacksForUser(userId.getLeastSignificantBits()));
    }
}

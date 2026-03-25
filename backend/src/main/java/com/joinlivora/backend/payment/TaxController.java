package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.TaxSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/tax")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TaxController {

    private final TaxService taxService;

    @GetMapping("/summary")
    public TaxSummaryDTO getTaxSummary(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        Instant start = (startDate != null) ? startDate : Instant.parse("1970-01-01T00:00:00Z");
        Instant end = (endDate != null) ? endDate : Instant.now();
        return taxService.getTaxSummary(start, end);
    }
}

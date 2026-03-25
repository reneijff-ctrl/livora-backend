package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.HighlightPricingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/chat/highlight/pricing")
@RequiredArgsConstructor
public class HighlightPricingController {

    @GetMapping("/pricing")
    public ResponseEntity<List<HighlightPricingResponse>> getPricing() {
        List<HighlightPricingResponse> pricing = Arrays.stream(HighlightType.values())
                .map(type -> HighlightPricingResponse.builder()
                        .type(type.name())
                        .minAmount(type.getMinimumAmount())
                        .currency("EUR")
                        .highlightDuration(type.getDurationSeconds())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(pricing);
    }
}

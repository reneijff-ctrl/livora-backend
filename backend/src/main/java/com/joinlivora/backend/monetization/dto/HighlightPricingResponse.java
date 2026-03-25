package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightPricingResponse {
    private String type;
    private BigDecimal minAmount;
    private String currency;
    private int highlightDuration;
}

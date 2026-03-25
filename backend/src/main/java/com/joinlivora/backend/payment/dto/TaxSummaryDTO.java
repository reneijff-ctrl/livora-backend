package com.joinlivora.backend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxSummaryDTO {
    private BigDecimal totalRevenue;
    private BigDecimal totalVat;
    private Map<String, BigDecimal> revenueByCountry;
}

package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAnomalyDTO {
    private UUID paymentId;
    private String userEmail;
    private BigDecimal amount;
    private String currency;
    private RiskLevel riskLevel;
    private Instant createdAt;
}

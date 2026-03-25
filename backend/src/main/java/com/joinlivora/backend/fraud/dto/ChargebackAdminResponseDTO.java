package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.ChargebackStatus;
import com.joinlivora.backend.user.FraudRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargebackAdminResponseDTO {
    private String userEmail;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private ChargebackStatus status;
    private Instant createdAt;
    private FraudRiskLevel fraudRisk;
}

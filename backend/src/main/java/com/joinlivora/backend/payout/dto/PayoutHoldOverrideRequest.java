package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payout.HoldLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutHoldOverrideRequest {
    private UUID subjectId;
    private RiskSubjectType subjectType;
    private HoldLevel holdLevel;
    private int holdDays;
    private String reason;
}

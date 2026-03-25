package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutHoldReleaseRequest {
    private UUID subjectId;
    private RiskSubjectType subjectType;
    private String reason;
}

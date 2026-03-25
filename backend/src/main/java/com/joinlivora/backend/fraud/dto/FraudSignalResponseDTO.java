package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudSignalResponseDTO {
    private UUID id;
    private Long userId;
    private String userEmail;
    private Long creatorId;
    private String creatorEmail;
    private FraudDecisionLevel riskLevel;
    private FraudSignalType type;
    private String reason;
    private Integer score;
    private Instant createdAt;
    private boolean resolved;
}

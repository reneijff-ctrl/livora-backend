package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.fraud.model.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamRiskStatusDTO {
    private UUID streamId;
    private Long creatorId;
    private String creatorUsername;
    private int viewerCount;
    private RiskLevel riskLevel;
    private boolean viewerSpike;
    private boolean suspiciousTips;
    private boolean chatSpam;
    private boolean newAccountCluster;
    private int riskScore;
}

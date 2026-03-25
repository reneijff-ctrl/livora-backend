package com.joinlivora.backend.moderation.dto;

import java.time.Instant;
import java.util.UUID;

public class ModerationDecisionDTO {

    private String type = "AI_MODERATION_DECISION";
    private UUID streamId;
    private String riskLevel;
    private int riskScore;
    private String action;
    private Instant timestamp = Instant.now();

    public ModerationDecisionDTO(
        UUID streamId,
        String riskLevel,
        int riskScore,
        String action
    ) {
        this.streamId = streamId;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.action = action;
    }

    public String getType() { return type; }
    public UUID getStreamId() { return streamId; }
    public String getRiskLevel() { return riskLevel; }
    public int getRiskScore() { return riskScore; }
    public String getAction() { return action; }
    public Instant getTimestamp() { return timestamp; }
}

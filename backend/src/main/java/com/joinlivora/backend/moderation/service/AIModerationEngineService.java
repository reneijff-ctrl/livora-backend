package com.joinlivora.backend.moderation.service;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.moderation.dto.ModerationDecisionDTO;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AIModerationEngineService {

    private final AdminRealtimeEventService adminRealtimeEventService;

    public AIModerationEngineService(
        AdminRealtimeEventService adminRealtimeEventService
    ) {
        this.adminRealtimeEventService = adminRealtimeEventService;
    }

    public ModerationDecisionDTO evaluateStreamRisk(
        UUID streamId,
        int viewerSpikeScore,
        int tipScore,
        int chatScore,
        int newAccountScore
    ) {

        int riskScore =
            viewerSpikeScore +
            tipScore +
            chatScore +
            newAccountScore;

        String riskLevel;
        String action = "NONE";

        if (riskScore >= 90) {
            riskLevel = "CRITICAL";
            action = "FREEZE_STREAM";
        }
        else if (riskScore >= 60) {
            riskLevel = "HIGH";
            action = "LIMIT_TIPS";
        }
        else if (riskScore >= 30) {
            riskLevel = "MEDIUM";
            action = "ENABLE_SLOW_MODE";
        }
        else {
            riskLevel = "LOW";
        }

        adminRealtimeEventService.publishAbuseEvent(
            "AI_MODERATION_DECISION",
            streamId,
            "",
            "Risk level: " + riskLevel
        );

        ModerationDecisionDTO decision = new ModerationDecisionDTO(
            streamId,
            riskLevel,
            riskScore,
            action
        );

        adminRealtimeEventService.publishModerationDecision(decision);

        return decision;
    }
}

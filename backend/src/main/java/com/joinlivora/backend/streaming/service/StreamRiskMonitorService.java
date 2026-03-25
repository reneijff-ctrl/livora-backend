package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.abuse.repository.AbuseEventRepository;
import com.joinlivora.backend.admin.dto.StreamRiskStatusDTO;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.websocket.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamRiskMonitorService {

    private final StreamRepository streamRepository;
    private final LiveViewerCounterService viewerCounterService;
    private final RuleFraudSignalRepository ruleFraudSignalRepository;
    private final AbuseEventRepository abuseEventRepository;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 10000)
    public void broadcastStreamRisks() {
        List<StreamRiskStatusDTO> risks = getAllStreamRisks();
        messagingTemplate.convertAndSend("/exchange/amq.topic/admin.streams", risks);
    }

    public List<StreamRiskStatusDTO> getAllStreamRisks() {
        return streamRepository.findActiveStreamsWithUser().stream()
                .map(this::evaluateRisk)
                .collect(Collectors.toList());
    }

    public StreamRiskStatusDTO getStreamRisk(UUID streamId) {
        Stream stream = streamRepository.findByIdWithCreator(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        return evaluateRisk(stream);
    }

    private StreamRiskStatusDTO evaluateRisk(Stream stream) {
        UUID streamId = stream.getId();
        Long creatorId = stream.getCreator().getId();
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));

        // 1. Viewer Spikes
        long currentViewers = viewerCounterService.getViewerCount(creatorId);
        Long previousViewers = viewerCounterService.getPreviousViewerCount(streamId);
        boolean viewerSpike = false;
        if (previousViewers != null) {
            long delta = currentViewers - previousViewers;
            // Using logic similar to ViewerSpikeDetectionService
            viewerSpike = delta >= 100 || (previousViewers > 0 && (double) delta / previousViewers >= 3.0);
        }

        // 2. Rapid Tipping / Clusters
        long rapidTipsCount = ruleFraudSignalRepository.countByCreatorIdAndTypeAndCreatedAtAfter(
                creatorId, FraudSignalType.RAPID_TIP_REPEATS, tenMinutesAgo);
        long tipClusterCount = ruleFraudSignalRepository.countByCreatorIdAndTypeAndCreatedAtAfter(
                creatorId, FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER, tenMinutesAgo);
        boolean suspiciousTips = rapidTipsCount > 0 || tipClusterCount > 0;

        // 3. Chat Spam
        long spamEvents = abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                new UUID(0L, creatorId), AbuseEventType.MESSAGE_SPAM, tenMinutesAgo);
        boolean chatSpam = spamEvents > 0;

        // 4. New Account Joins
        long newAccountJoins = presenceService.getRecentNewAccountJoinCount(creatorId, Duration.ofMinutes(10));
        boolean newAccountCluster = newAccountJoins >= 5;

        // Scoring
        int score = 0;
        if (viewerSpike) score += 30;
        if (suspiciousTips) score += 40;
        if (chatSpam) score += 20;
        if (newAccountCluster) score += 35;

        // Risk Level mapping:
        // 0-30 LOW
        // 30-60 MEDIUM
        // 60-90 HIGH
        // 90+ CRITICAL
        RiskLevel riskLevel;
        if (score >= 90) riskLevel = RiskLevel.CRITICAL;
        else if (score >= 60) riskLevel = RiskLevel.HIGH;
        else if (score >= 30) riskLevel = RiskLevel.MEDIUM;
        else riskLevel = RiskLevel.LOW;

        return StreamRiskStatusDTO.builder()
                .streamId(streamId)
                .creatorId(creatorId)
                .creatorUsername(stream.getCreator().getUsername())
                .viewerCount((int) currentViewers)
                .riskLevel(riskLevel)
                .viewerSpike(viewerSpike)
                .suspiciousTips(suspiciousTips)
                .chatSpam(chatSpam)
                .newAccountCluster(newAccountCluster)
                .riskScore(score)
                .build();
    }
}

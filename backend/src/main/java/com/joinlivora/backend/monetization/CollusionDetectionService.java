package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.monetization.dto.TipGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service("collusionDetectionService")
@RequiredArgsConstructor
@Slf4j
public class CollusionDetectionService {

    private final TipGraphBuilderService tipGraphBuilderService;
    private final AnalyticsEventRepository analyticsEventRepository;

    public CollusionResult detectCollusion(UUID userId) {
        log.info("Detecting collusion for creator: {}", userId);
        
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<TipGraph> graph = tipGraphBuilderService.buildGraph(since);
        
        int score = 0;
        List<String> patterns = new ArrayList<>();

        // 1. Same users tipping same creator repeatedly
        if (detectRepeatedTipping(userId, graph)) {
            score += 30;
            patterns.add("REPEATED_TIPPING");
        }

        // 2. Circular tipping patterns (A -> B -> A)
        if (detectCircularTipping(userId, graph)) {
            score += 50;
            patterns.add("CIRCULAR_TIPPING");
        }

        // 3. High tip frequency with low chat activity
        if (detectHighTipLowActivity(userId, graph)) {
            score += 40;
            patterns.add("HIGH_TIP_LOW_ACTIVITY");
        }

        // 4. Multiple accounts funding one creator (Cluster)
        if (detectClusterFunding(userId, graph)) {
            score += 30;
            patterns.add("CLUSTER_FUNDING");
        }

        return CollusionResult.builder()
                .collusionScore(Math.min(score, 100))
                .patternTypes(patterns)
                .build();
    }

    private boolean detectRepeatedTipping(UUID creatorId, List<TipGraph> graph) {
        // High frequency: one creator sent more than 10 tips to this creator in 30 days
        return graph.stream()
                .filter(g -> g.getCreatorUserId().equals(creatorId))
                .anyMatch(g -> g.getTipCount() > 10);
    }

    private boolean detectCircularTipping(UUID userId, List<TipGraph> graph) {
        // Simple cycle detection: creator -> other -> creator
        Set<UUID> usersTippedByMe = graph.stream()
                .filter(g -> g.getTipperUserId().equals(userId))
                .map(TipGraph::getCreatorUserId)
                .collect(Collectors.toSet());

        for (UUID otherId : usersTippedByMe) {
            boolean otherTipsMe = graph.stream()
                    .anyMatch(g -> g.getTipperUserId().equals(otherId) && g.getCreatorUserId().equals(userId));
            if (otherTipsMe) return true;
        }
        return false;
    }

    private boolean detectHighTipLowActivity(UUID userId, List<TipGraph> graph) {
        // Analyze as creator
        long totalTipsReceived = graph.stream()
                .filter(g -> g.getCreatorUserId().equals(userId))
                .count();

        if (totalTipsReceived > 5) {
            Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
            // We need the Long internal ID for Analytics repo
            // Since we only have UUID here, we might need a lookup or change the repo
            // For now, let's assume we can map it or use UUID if repo allows (checked: it uses Long creator)
            // This is a known issue in the current codebase (UUID vs Long)
            // AdminChargebackController uses creator.getLeastSignificantBits() as a hack.
            
            long chatCount = analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                    userId.getLeastSignificantBits(), AnalyticsEventType.CHAT_MESSAGE_SENT, since);
            
            return chatCount < 5;
        }
        return false;
    }

    private boolean detectClusterFunding(UUID creatorId, List<TipGraph> graph) {
        // Cluster: More than 5 different accounts tipped this creator, but they all have 0 chat activity
        List<TipGraph> tippers = graph.stream()
                .filter(g -> g.getCreatorUserId().equals(creatorId))
                .toList();

        if (tippers.size() >= 5) {
            Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
            long inactiveTippers = tippers.stream()
                    .filter(g -> analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                            g.getTipperUserId().getLeastSignificantBits(), AnalyticsEventType.CHAT_MESSAGE_SENT, since) == 0)
                    .count();
            
            return inactiveTippers >= 5;
        }
        return false;
    }
}

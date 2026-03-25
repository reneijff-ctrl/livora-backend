package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.moderation.service.AIModerationEngineService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViewerSpikeDetectionService {

    private final StreamRepository streamRepository;
    private final LiveViewerCounterService viewerCounterService;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final AIModerationEngineService aiModerationEngineService;

    // Stores previous viewer counts to detect spikes over a 30s window
    private final Map<Long, Long> previousViewerCounts = new HashMap<>();

    /**
     * Scheduled task to detect abnormal viewer spikes every 30 seconds.
     * Triggers warnings or critical alerts based on absolute increase or percentage growth.
     */
    @Scheduled(fixedRate = 30000)
    public void detectViewerSpikes() {
        List<Stream> activeStreams = streamRepository.findActiveStreamsWithUser();
        if (activeStreams.isEmpty()) {
            previousViewerCounts.clear();
            return;
        }

        for (Stream stream : activeStreams) {
            Long creatorId = stream.getCreator().getId();
            long currentCount = viewerCounterService.getViewerCount(creatorId);
            Long previousCount = previousViewerCounts.get(creatorId);

            if (previousCount != null) {
                long delta = currentCount - previousCount;
                
                // Trigger conditions: >= 100 viewers increase OR >= 300% growth
                boolean exceedsDeltaThreshold = delta >= 100;
                boolean exceedsGrowthThreshold = previousCount > 0 && ((double) delta / previousCount) >= 3.0;

                if (exceedsDeltaThreshold || exceedsGrowthThreshold) {
                    log.warn("VIEWER_SPIKE_DETECTED: stream={}, creator={}, delta={}, growth={}x", 
                            stream.getId(), creatorId, delta, (previousCount > 0 ? (double) delta / previousCount : "inf"));
                    
                    adminRealtimeEventService.publishViewerSpike(stream.getId(), (int) delta);

                    adminRealtimeEventService.publishAbuseEvent(
                        "VIEWER_SPIKE",
                        stream.getId(),
                        stream.getCreator().getUsername(),
                        "Viewer spike detected"
                    );

                    aiModerationEngineService.evaluateStreamRisk(
                        stream.getId(),
                        30,
                        0,
                        0,
                        0
                    );
                }
            }
            
            // Record history in Redis for persistent spike detection
            viewerCounterService.recordViewerHistory(stream.getId(), currentCount);
            
            previousViewerCounts.put(creatorId, currentCount);
        }

        // Cleanup: remove counts for streams that are no longer live
        previousViewerCounts.keySet().retainAll(
            activeStreams.stream().map(s -> s.getCreator().getId()).collect(Collectors.toSet())
        );
    }
}

package com.joinlivora.backend.livestream.listener;

import com.joinlivora.backend.livestream.event.StreamEndedEventV2;
import com.joinlivora.backend.livestream.event.StreamStartedEventV2;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.streaming.service.StreamShardingService;
import com.joinlivora.backend.streaming.service.ViewerLoadSheddingService;
import com.joinlivora.backend.streaming.service.StreamAnalyticsService;
import com.joinlivora.backend.streaming.service.HlsPreWarmService;
import com.joinlivora.backend.websocket.RealtimeMessage;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.streaming.service.StreamCacheService;
import com.joinlivora.backend.streaming.transcode.TranscodeJobPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Listener for livestream-related events.
 * Decouples core business logic from broadcasting, presence, analytics, and notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LivestreamEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final CreatorPresenceService presenceService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final CreatorRepository creatorRepository;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final StreamModeratorService streamModeratorService;
    private final TranscodeJobPublisher transcodeJobPublisher;
    private final StreamCacheService streamCacheService;
    private final StreamShardingService streamShardingService;
    private final ViewerLoadSheddingService viewerLoadSheddingService;
    private final StreamAnalyticsService streamAnalyticsService;
    private final HlsPreWarmService hlsPreWarmService;

    @EventListener
    @Async
    public void handleStreamStartedV2(StreamStartedEventV2 event) {
        log.info("Handling StreamStartedEventV2 for stream {} creator {}", event.getStreamId(), event.getCreatorUserId());
        Long creatorUserId = event.getCreatorUserId();

        // Reset UUID-based Redis viewer keys for the new stream
        liveViewerCounterService.resetViewerCountByStreamId(event.getStreamId(), creatorUserId);

        // Pre-warm CDN cache — fires async HTTP requests before any viewer arrives
        hlsPreWarmService.preWarmStream(event.getStreamId());

        // Clear any stale shard data from a previous stream run
        streamShardingService.cleanupStreamShards(event.getStreamId());

        // Initialize analytics for this stream
        streamAnalyticsService.recordViewerEvent(event.getStreamId(), creatorUserId, "stream-start");

        // Broadcast presence update using UUID stream id
        broadcastStreamStateV2(creatorUserId, "LIVE", event.getStreamId());

        // Broadcast presence update (ONLINE and LIVE)
        creatorRepository.findByUser_Id(creatorUserId).ifPresent(creator -> {
            presenceService.markOnline(creator.getId());
            Map<String, Object> presenceUpdate = Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creator.getId(),
                    "online", true,
                    "availability", "LIVE"
            );
            messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence", RealtimeMessage.of("presence:update", presenceUpdate));
        });
    }

    @EventListener
    @Async
    public void handleStreamEndedV2(StreamEndedEventV2 event) {
        log.info("Handling StreamEndedEventV2 for stream {} creator {} reason={}", event.getStreamId(), event.getCreatorUserId(), event.getReason());
        Long creatorUserId = event.getCreatorUserId();

        // Cleanup UUID-based Redis viewer keys
        liveViewerCounterService.resetViewerCountByStreamId(event.getStreamId(), creatorUserId);

        // Broadcast stream ended status
        broadcastStreamStateV2(creatorUserId, "ENDED", event.getStreamId());

        // Broadcast presence update (no longer LIVE)
        creatorRepository.findByUser_Id(creatorUserId).ifPresent(creator -> {
            Map<String, Object> presenceUpdate = Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creator.getId(),
                    "online", true,
                    "availability", "ONLINE"
            );
            messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence", RealtimeMessage.of("presence:update", presenceUpdate));
        });

        // Clear chat history buffer
        try {
            redisTemplate.delete("chat:history:" + creatorUserId);
        } catch (Exception e) {
            log.warn("Failed to clear chat history buffer for creator {}: {}", creatorUserId, e.getMessage());
        }

        // Clear stream-only moderators
        try {
            streamModeratorService.clearSessionModerators(creatorUserId);
            log.info("LIVESTREAM-EVENT: Cleared session moderators for creator {} (V2)", creatorUserId);
        } catch (Exception e) {
            log.warn("Failed to clear session moderators for creator {} (V2): {}", creatorUserId, e.getMessage());
        }

        // Remove stream from Redis ZSET cache (explore page)
        streamCacheService.removeStream(event.getStreamId());

        // Signal the transcode-worker to stop the FFmpeg process and clean up HLS files
        transcodeJobPublisher.publishStopSignal(event.getStreamId());

        // Clean up sharded viewer keys
        streamShardingService.cleanupStreamShards(event.getStreamId());

        // Clear load shedding flags
        viewerLoadSheddingService.clearSheddingState(event.getStreamId());

        // Finalize analytics — keep metrics for 7 days, mark stream as ended
        streamAnalyticsService.finalizeStreamAnalytics(event.getStreamId());
    }

    private void broadcastStreamStateV2(Long creatorUserId, String status, java.util.UUID streamId) {
        RealtimeMessage streamUpdate = RealtimeMessage.builder()
                .type("stream:state:update")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "creatorUserId", creatorUserId,
                        "streamId", streamId.toString(),
                        "status", status
                ))
                .build();

        messagingTemplate.convertAndSend("/exchange/amq.topic/stream.v2.creator." + creatorUserId + ".status", streamUpdate);
        messagingTemplate.convertAndSend("/exchange/amq.topic/stream.v2.stream." + streamId + ".status", streamUpdate);
    }

}

package com.joinlivora.backend.livestream.listener;

import com.joinlivora.backend.livestream.event.StreamEndedEvent;
import com.joinlivora.backend.livestream.event.StreamStartedEvent;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.websocket.RealtimeMessage;
import com.joinlivora.backend.creator.repository.CreatorRepository;
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

    @EventListener
    @Async
    public void handleStreamStarted(StreamStartedEvent event) {
        var session = event.getSession();
        Long creatorUserId = session.getCreator().getId();
        log.info("LIVESTREAM-EVENT: Stream started for creator {}", creatorUserId);

        // 1. WebSocket broadcaster
        broadcastStreamState(creatorUserId, "LIVE", session.getId());
        
        // Broadcast presence update (ONLINE and LIVE)
        creatorRepository.findByUser_Id(creatorUserId).ifPresent(creator -> {
            Long creatorId = creator.getId();
            
            // 2. Presence updater
            presenceService.markOnline(creatorId);
            
            // Broadcasters for general discovery topics
            Map<String, Object> presenceUpdate = Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creatorId,
                    "online", true,
                    "availability", "LIVE"
            );
            messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence", RealtimeMessage.of("presence:update", presenceUpdate));
        });

        // 3. Cleanup Redis state for new session
        liveViewerCounterService.resetViewerCount(session.getId(), creatorUserId);
        
        // 4. Notification dispatcher
        log.info("LIVESTREAM-EVENT: Dispatching notifications for stream start of creator {}", creatorUserId);
        // (Placeholder for actual notification service call)
    }

    @EventListener
    @Async
    public void handleStreamEnded(StreamEndedEvent event) {
        var session = event.getSession();
        Long creatorUserId = session.getCreator().getId();
        log.info("LIVESTREAM-EVENT: Stream ended for creator {}", creatorUserId);

        // 1. WebSocket broadcaster
        broadcastStreamState(creatorUserId, "ENDED", session.getId());
        
        // Broadcast presence update (still ONLINE but no longer LIVE)
        creatorRepository.findByUser_Id(creatorUserId).ifPresent(creator -> {
            Long creatorId = creator.getId();
            
            // 2. Presence updater (sync availability)
            // We don't mark offline because they might still be in chat or browse, 
            // but availability will resolve to ONLINE instead of LIVE automatically.
            
            Map<String, Object> presenceUpdate = Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creatorId,
                    "online", true,
                    "availability", "ONLINE"
            );
            messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence", RealtimeMessage.of("presence:update", presenceUpdate));
        });

        // 3. Cleanup Redis state
        liveViewerCounterService.resetViewerCount(session.getId(), creatorUserId);

        // 4. Clear chat history buffer
        try {
            redisTemplate.delete("chat:history:" + creatorUserId);
        } catch (Exception e) {
            log.warn("Failed to clear chat history buffer for creator {}: {}", creatorUserId, e.getMessage());
        }

        // 5. Clear stream-only moderators
        try {
            streamModeratorService.clearSessionModerators(creatorUserId);
            log.info("LIVESTREAM-EVENT: Cleared session moderators for creator {}", creatorUserId);
        } catch (Exception e) {
            log.warn("Failed to clear session moderators for creator {}: {}", creatorUserId, e.getMessage());
        }

        // 6. Notification dispatcher
        log.info("LIVESTREAM-EVENT: Dispatching notifications for stream end of creator {}", creatorUserId);
        // (Placeholder for actual notification service call)
    }

    private void broadcastStreamState(Long creatorUserId, String status, Long sessionId) {
        RealtimeMessage streamUpdate = RealtimeMessage.builder()
                .type("stream:state:update")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "creatorUserId", creatorUserId,
                        "sessionId", sessionId,
                        "status", status
                ))
                .build();
        
        messagingTemplate.convertAndSend("/exchange/amq.topic/stream.v2.creator." + creatorUserId + ".status", streamUpdate);
        
        // Also broadcast to the session-specific topic
        messagingTemplate.convertAndSend("/exchange/amq.topic/stream.v2.session." + sessionId + ".status", streamUpdate);
    }
}

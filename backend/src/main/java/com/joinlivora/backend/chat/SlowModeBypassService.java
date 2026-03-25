package com.joinlivora.backend.chat;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SlowModeBypassService {

    private final SlowModeBypassRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    public SlowModeBypassService(
            SlowModeBypassRepository repository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            AnalyticsEventPublisher analyticsEventPublisher) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    @Transactional
    public void grantBypass(User user, Stream room, int durationSeconds, SlowModeBypassSource source) {
        log.info("CHAT: Granting slow mode bypass to {} in room {} for {}s (Source: {})", 
                user.getEmail(), room.getId(), durationSeconds, source);
        
        Instant now = Instant.now();
        SlowModeBypass bypass = repository.findActiveByUserIdAndRoomId(user.getId(), room.getId(), now)
                .map(existing -> {
                    // Extend existing
                    existing.setExpiresAt(existing.getExpiresAt().plusSeconds(durationSeconds));
                    existing.setSource(source);
                    log.info("CHAT: Extended existing bypass for {}. New expiry: {}", user.getEmail(), existing.getExpiresAt());
                    return repository.save(existing);
                })
                .orElseGet(() -> {
                    // Create new
                    SlowModeBypass newBypass = SlowModeBypass.builder()
                            .userId(user)
                            .roomId(room)
                            .expiresAt(now.plusSeconds(durationSeconds))
                            .source(source)
                            .build();
                    return repository.save(newBypass);
                });

        // Broadcast event (Using creatorId routing)
        RealtimeMessage event = RealtimeMessage.of("SLOW_MODE_BYPASS_GRANTED", Map.of(
                "roomId", room.getId(),
                "creator", user.getId(),
                "expiresAt", bypass.getExpiresAt()
        ));
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + room.getCreator().getId(), event);

        // Analytics
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.SLOW_MODE_BYPASS_GRANTED,
                user,
                Map.of(
                        "roomId", room.getId(),
                        "creator", user.getId(),
                        "source", source.name(),
                        "durationSeconds", durationSeconds
                )
        );
    }

    public boolean isBypassing(Long userId, UUID roomId) {
        return repository.findActiveByUserIdAndRoomId(userId, roomId, Instant.now()).isPresent();
    }

    @Transactional
    public void revokeBypass(Long userId, UUID roomId) {
        log.info("CHAT: Revoking slow mode bypass for creator {} in room {}", userId, roomId);
        repository.findActiveByUserIdAndRoomId(userId, roomId, Instant.now())
                .ifPresent(bypass -> {
                    repository.delete(bypass);

                    // Broadcast event (Using creatorId routing)
                    RealtimeMessage event = RealtimeMessage.of("SLOW_MODE_BYPASS_REVOKED", Map.of(
                            "roomId", roomId,
                            "creator", userId
                    ));
                    messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + bypass.getRoomId().getCreator().getId(), event);

                    // Analytics
                    analyticsEventPublisher.publishEvent(
                            AnalyticsEventType.SLOW_MODE_BYPASS_REVOKED,
                            bypass.getUserId(),
                            Map.of(
                                    "roomId", roomId,
                                    "creator", userId,
                                    "source", bypass.getSource().name()
                            )
                    );
                });
    }

    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now();
        List<SlowModeBypass> expired = repository.findAllByExpiresAtBefore(now);
        
        for (SlowModeBypass bypass : expired) {
            long totalDuration = Duration.between(bypass.getCreatedAt(), bypass.getExpiresAt()).toSeconds();
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.SLOW_MODE_BYPASS_EXPIRED,
                    bypass.getUserId(),
                    Map.of(
                            "roomId", bypass.getRoomId().getId(),
                            "creator", bypass.getUserId().getId(),
                            "source", bypass.getSource().name(),
                            "durationSeconds", totalDuration
                    )
            );
        }

        if (!expired.isEmpty()) {
            repository.deleteExpired(now);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduledCleanup() {
        log.debug("CHAT: Running scheduled cleanup of expired slow mode bypasses");
        cleanupExpired();
    }
}

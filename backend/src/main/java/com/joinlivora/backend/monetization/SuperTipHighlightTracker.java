package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.websocket.LiveEvent;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class SuperTipHighlightTracker {

    private final SimpMessagingTemplate messagingTemplate;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;

    public SuperTipHighlightTracker(
            SimpMessagingTemplate messagingTemplate,
            com.joinlivora.backend.streaming.StreamRepository streamRepository) {
        this.messagingTemplate = messagingTemplate;
        this.streamRepository = streamRepository;
    }
    
    // Active highlight per room
    private final Map<UUID, SuperTipResponse> activeHighlights = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> expiryTimes = new ConcurrentHashMap<>();
    
    // Queue of pending highlights per room to prevent overlapping
    private final Map<UUID, Queue<SuperTipResponse>> pendingQueues = new ConcurrentHashMap<>();

    /**
     * Adds a new SuperTip highlight to the tracker.
     * If no highlight is active for the room, it starts immediately.
     * Otherwise, it's queued.
     */
    public void addHighlight(UUID roomId, SuperTipResponse superTip) {
        pendingQueues.computeIfAbsent(roomId, k -> new ConcurrentLinkedQueue<>()).add(superTip);
        tryProcessNext(roomId);
    }

    /**
     * Returns the currently active highlight for a room, if any.
     */
    public Optional<SuperTipResponse> getActiveHighlight(UUID roomId) {
        SuperTipResponse active = activeHighlights.get(roomId);
        if (active != null) {
            Instant expiry = expiryTimes.get(roomId);
            if (expiry != null && expiry.isAfter(Instant.now())) {
                return Optional.of(active);
            }
        }
        return Optional.empty();
    }

    private synchronized void tryProcessNext(UUID roomId) {
        if (activeHighlights.containsKey(roomId)) {
            // Already showing something, wait for expiration
            return;
        }

        Queue<SuperTipResponse> queue = pendingQueues.get(roomId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        SuperTipResponse next = queue.poll();
        if (next != null) {
            startHighlight(roomId, next);
        }
    }

    private void startHighlight(UUID roomId, SuperTipResponse superTip) {
        log.info("HIGHLIGHTED_CHAT: Starting highlight for room {} from {}", roomId, superTip.getSenderUsername());
        activeHighlights.put(roomId, superTip);
        expiryTimes.put(roomId, Instant.now().plusSeconds(superTip.getDurationSeconds()));
        
        // Broadcast the highlight start event
        broadcastHighlightStarted(roomId, superTip);
    }

    private void broadcastHighlightStarted(UUID roomId, SuperTipResponse result) {
        // Resolve creatorId from unified Stream identity
        Long creatorId = streamRepository.findById(roomId)
                .map(stream -> stream.getCreator().getId())
                .orElseGet(() -> streamRepository.findByMediasoupRoomId(roomId)
                        .map(stream -> stream.getCreator().getId())
                        .orElse(null));
        
        if (creatorId == null) return;

        RealtimeMessage event = RealtimeMessage.of("SUPER_TIP", Map.of(
                "id", result.getId(),
                "viewerId", result.getSenderId(),
                "viewer", result.getSenderUsername(),
                "amount", result.getAmount(),
                "message", result.getMessage() != null ? result.getMessage() : "",
                "highlightLevel", result.getHighlightLevel().name(),
                "highlightColor", result.getHighlightLevel().getHighlightColor(),
                "durationSeconds", result.getDurationSeconds(),
                "highlightDuration", result.getDurationSeconds(),
                "priority", result.getHighlightLevel().isPriority()
        ));
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, event);
        // Broadcast to dedicated monetization stream
        messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorId,
                LiveEvent.of(result.getId().toString(), "SUPER_TIP", event.getPayload()));
    }

    @Scheduled(fixedDelay = 1000) // Check every second
    public void cleanupExpiredHighlights() {
        Instant now = Instant.now();
        for (UUID roomId : activeHighlights.keySet()) {
            Instant expiry = expiryTimes.get(roomId);
            if (expiry != null && now.isAfter(expiry)) {
                expireHighlight(roomId);
            }
        }
    }

    private synchronized void expireHighlight(UUID roomId) {
        // Resolve creatorId from unified Stream identity
        Long creatorId = streamRepository.findById(roomId)
                .map(stream -> stream.getCreator().getId())
                .orElseGet(() -> streamRepository.findByMediasoupRoomId(roomId)
                        .map(stream -> stream.getCreator().getId())
                        .orElse(null));
        
        log.info("HIGHLIGHTED_CHAT: Expired highlight for room {}", roomId);
        activeHighlights.remove(roomId);
        expiryTimes.remove(roomId);
        
        // Notify frontend that highlight ended
        if (creatorId != null) {
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, 
                    RealtimeMessage.of("SUPER_TIP_END", Map.of("roomId", roomId)));
            // Broadcast to dedicated monetization stream
            messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorId,
                    LiveEvent.of("SUPER_TIP_END", Map.of("roomId", roomId.toString())));
        }
        
        // Try to process next in queue
        tryProcessNext(roomId);
    }
}

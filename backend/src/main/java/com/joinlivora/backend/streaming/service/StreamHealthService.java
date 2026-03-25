package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupRoom;
import com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupRoomsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamHealthService {

    private final MediasoupClient mediasoupClient;
    private final LiveViewerCounterService viewerCounterService;
    private final StreamRepository streamRepository;
    private static final int DRIFT_THRESHOLD = 5;

    @Scheduled(fixedRate = 60000) // Every minute
    public void verifyMediasoupViewerCounts() {
        log.info("LIVESTREAM-HEALTH: Starting Mediasoup ground-truth verification...");
        mediasoupClient.getRooms().thenAccept(response -> {
            if (response == null || response.getRooms() == null) {
                log.warn("LIVESTREAM-HEALTH: Could not retrieve room information from Mediasoup.");
                return;
            }

            List<MediasoupRoom> rooms = response.getRooms();
            for (MediasoupRoom room : rooms) {
                try {
                    processRoomVerification(room);
                } catch (Exception e) {
                    log.error("LIVESTREAM-HEALTH: Error verifying room {}: {}", room.getRoomId(), e.getMessage());
                }
            }
        });
    }

    private void processRoomVerification(MediasoupRoom room) {
        String roomIdStr = room.getRoomId();
        UUID mediasoupRoomId;
        try {
            mediasoupRoomId = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException e) {
            log.debug("LIVESTREAM-HEALTH: Skipping non-UUID roomId: {}", roomIdStr);
            return;
        }

        // Map Mediasoup UUID back to Creator User ID
        streamRepository.findByMediasoupRoomId(mediasoupRoomId).ifPresent(stream -> {
            if (!stream.isLive()) {
                log.warn("LIVESTREAM-HEALTH: Found orphaned Mediasoup room {} for offline stream {}. Requesting cleanup.", 
                        mediasoupRoomId, stream.getId());
                // In a real scenario, we might want to call LiveStreamService.cleanupMediasoupRoom here
                return;
            }

            Long creatorUserId = stream.getCreator().getId();
            long redisCount = viewerCounterService.getViewerCount(creatorUserId);
            int mediasoupCount = room.getConsumers();
            long drift = Math.abs(redisCount - mediasoupCount);

            if (drift > DRIFT_THRESHOLD) {
                log.warn("LIVESTREAM-HEALTH: DRIFT DETECTED for creatorId={} (room={})! Redis={}, Mediasoup={}. Drift={}", 
                        creatorUserId, mediasoupRoomId, redisCount, mediasoupCount, drift);
                
                reconcile(creatorUserId, mediasoupCount, (int) redisCount);
            } else {
                log.debug("LIVESTREAM-HEALTH: Room {} is healthy. Redis={}, Mediasoup={}", 
                        creatorUserId, redisCount, mediasoupCount);
            }
        });
    }

    private void reconcile(Long creatorUserId, int groundTruthCount, int oldRedisCount) {
        log.info("LIVESTREAM-HEALTH: Reconciling viewer count for creatorId={} using Mediasoup ground-truth ({} consumers, Redis had {})", 
                creatorUserId, groundTruthCount, oldRedisCount);
        
        // Update Redis (broadcast-only for now, but could be extended to sync sessions if needed)
        viewerCounterService.reconcileCount(creatorUserId, groundTruthCount);
    }
}

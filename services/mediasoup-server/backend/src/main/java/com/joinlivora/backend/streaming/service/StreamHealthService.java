package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupRoom;
import com.joinlivora.backend.streaming.client.MediasoupClient.MediasoupRoomsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamHealthService {

    private final MediasoupClient mediasoupClient;
    private final LiveViewerCounterService viewerCounterService;
    private static final int DRIFT_THRESHOLD = 5;

    @Scheduled(fixedRate = 60000)
    public void verifyMediasoupViewerCounts() {
        log.info("LIVESTREAM-HEALTH: Starting Mediasoup ground-truth verification...");
        MediasoupRoomsResponse response = mediasoupClient.getRooms();
        
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
    }

    private void processRoomVerification(MediasoupRoom room) {
        String roomIdStr = room.getRoomId();
        Long creatorUserId;
        try {
            creatorUserId = Long.valueOf(roomIdStr);
        } catch (NumberFormatException e) {
            log.debug("LIVESTREAM-HEALTH: Skipping non-numeric roomId: {}", roomIdStr);
            return;
        }

        long redisCount = viewerCounterService.getViewerCount(creatorUserId);
        int mediasoupCount = room.getConsumers();
        long drift = Math.abs(redisCount - mediasoupCount);

        if (drift > DRIFT_THRESHOLD) {
            log.warn("LIVESTREAM-HEALTH: DRIFT DETECTED for creatorId={}! Redis={}, Mediasoup={}. Drift={}", 
                    creatorUserId, redisCount, mediasoupCount, drift);
            
            reconcile(creatorUserId, mediasoupCount, (int) redisCount);
        } else {
            log.debug("LIVESTREAM-HEALTH: Room {} is healthy. Redis={}, Mediasoup={}", 
                    creatorUserId, redisCount, mediasoupCount);
        }
    }

    private void reconcile(Long creatorUserId, int groundTruthCount, int oldRedisCount) {
        log.info("LIVESTREAM-HEALTH: Reconciling viewer count for creatorId={} using Mediasoup ground-truth ({} consumers, Redis had {})", 
                creatorUserId, groundTruthCount, oldRedisCount);
        
        // Update DB and broadcast the ground-truth count.
        viewerCounterService.reconcileCount(creatorUserId, groundTruthCount);
    }
}

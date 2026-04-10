package com.joinlivora.backend.streaming;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamRoomStartupCleanup {

    private final StreamRepository streamRepository;
    private final com.joinlivora.backend.streaming.service.LiveViewerCounterService liveViewerCounterService;
    private final LiveStreamService liveStreamService;

    @PostConstruct
    public void cleanup() {
        // Reset unified stream live flags for any streams that were interrupted
        List<Stream> activeStreams = streamRepository.findActiveStreamsWithUser();
        activeStreams.forEach(stream -> {
            stream.setLive(false);
            if (stream.getEndedAt() == null) {
                stream.setEndedAt(java.time.Instant.now());
            }
            // Clear UUID-based active stream pointer from Redis
            if (stream.getCreator() != null) {
                liveViewerCounterService.resetViewerCountByStreamId(stream.getId(), stream.getCreator().getId());
            }
            // Cleanup orphan Mediasoup rooms on the SFU
            if (stream.getMediasoupRoomId() != null) {
                liveStreamService.cleanupMediasoupRoom(stream.getMediasoupRoomId());
            }
        });
        streamRepository.saveAll(activeStreams);
        log.info("STARTUP_CLEANUP: Ended {} interrupted streams", activeStreams.size());
    }
}

package com.joinlivora.backend.streaming;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StreamRoomStartupCleanup {

    private final StreamRepository streamRepository;
    private final LivestreamSessionRepository sessionRepository;
    private final com.joinlivora.backend.streaming.service.LiveViewerCounterService liveViewerCounterService;
    private final LiveStreamService liveStreamService;

    @PostConstruct
    public void cleanup() {
        // Reset unified stream live flags
        List<Stream> activeStreams = streamRepository.findActiveStreamsWithUser();
        activeStreams.forEach(stream -> {
            stream.setLive(false);
            if (stream.getEndedAt() == null) {
                stream.setEndedAt(java.time.Instant.now());
            }
            // Reset viewer count for each interrupted stream
            if (stream.getCreator() != null) {
                // If we don't have the sessionId, we can't clean up specific keys easily,
                // but those keys have TTL anyway. We at least clear the active session mapping.
                liveViewerCounterService.setActiveSession(stream.getCreator().getId(), null);
            }
            // Cleanup orphan Mediasoup rooms on the SFU
            if (stream.getMediasoupRoomId() != null) {
                liveStreamService.cleanupMediasoupRoom(stream.getMediasoupRoomId());
            }
        });
        streamRepository.saveAll(activeStreams);

        // End any lingering LIVE sessions
        sessionRepository.findAllByStatus(LivestreamStatus.LIVE).forEach(session -> {
            session.end();
            sessionRepository.save(session);
            liveViewerCounterService.resetViewerCount(session.getId(), session.getCreator().getId());
        });
    }
}

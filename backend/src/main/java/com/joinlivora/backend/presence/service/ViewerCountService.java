package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Service for managing stream viewer counts.
 * Responsibilities:
 * - increment viewer count
 * - decrement viewer count
 * - retrieve viewer count for streams
 */
@Service
@RequiredArgsConstructor
public class ViewerCountService {

    private final LiveViewerCounterService liveViewerCounterService;

    // -------------------------------------------------------------------------
    // UUID-based API (new — Stream as single source of truth)
    // -------------------------------------------------------------------------

    public void incrementViewerCount(UUID streamId, Long creatorUserId, Long viewerUserId, String sessionId, String ip, String userAgent) {
        if (liveViewerCounterService != null) {
            liveViewerCounterService.addViewer(streamId, creatorUserId, viewerUserId, sessionId, ip, userAgent);
        }
    }

    public void decrementViewerCount(UUID streamId, Long creatorUserId, Long viewerUserId, String sessionId, String ip, String userAgent) {
        if (liveViewerCounterService != null) {
            liveViewerCounterService.removeViewer(streamId, creatorUserId, viewerUserId, sessionId, ip, userAgent);
        }
    }


    public long getViewerCount(Long creatorUserId) {
        return liveViewerCounterService != null ? liveViewerCounterService.getViewerCount(creatorUserId) : 0;
    }

    public Set<String> getViewers(Long creatorUserId) {
        return liveViewerCounterService != null ? liveViewerCounterService.getViewers(creatorUserId) : java.util.Collections.emptySet();
    }
}

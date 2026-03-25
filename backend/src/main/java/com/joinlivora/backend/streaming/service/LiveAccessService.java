package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.streaming.LiveAccess;
import com.joinlivora.backend.streaming.LiveAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * LiveAccessService - Manages viewership access control for live streams.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAccessService {

    private final LiveAccessRepository repository;

    /**
     * Checks if a viewer has active access to a creator's stream.
     * 
     * @param creatorUserId The ID of the creator (User ID).
     * @param viewerUserId The ID of the viewer (User ID).
     * @return true if access is granted and not expired, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(Long creatorUserId, Long viewerUserId) {
        if (creatorUserId == null || viewerUserId == null) return false;
        
        // A creator always has access to their own stream
        if (creatorUserId.equals(viewerUserId)) return true;
        
        return repository.findByCreatorUserIdAndViewerUserId(creatorUserId, viewerUserId)
                .map(access -> access.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * Grants access to a viewer for a specific duration.
     * If existing access exists and is still valid, the duration is added to the existing expiration.
     * Otherwise, the duration is added from the current time.
     * 
     * @param creatorUserId The ID of the creator (User ID).
     * @param viewerUserId The ID of the viewer (User ID).
     * @param duration The duration for which access is granted.
     */
    @Transactional
    public void grantAccess(Long creatorUserId, Long viewerUserId, Duration duration) {
        if (creatorUserId == null || viewerUserId == null || duration == null) return;

        LiveAccess access = repository.findByCreatorUserIdAndViewerUserId(creatorUserId, viewerUserId)
                .orElse(LiveAccess.builder()
                        .creatorUserId(creatorUserId)
                        .viewerUserId(viewerUserId)
                        .build());

        Instant now = Instant.now();
        Instant currentExpiry = access.getExpiresAt();
        
        if (currentExpiry == null || currentExpiry.isBefore(now)) {
            access.setExpiresAt(now.plus(duration));
        } else {
            access.setExpiresAt(currentExpiry.plus(duration));
        }

        repository.save(access);
        log.info("LIVESTREAM-ACCESS: Granted {} duration access to viewer {} for creator {}", 
                duration, viewerUserId, creatorUserId);
    }
}

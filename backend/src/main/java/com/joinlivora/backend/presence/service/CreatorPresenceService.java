package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.presence.model.CreatorAvailabilityStatus;
import com.joinlivora.backend.presence.entity.CreatorPresence;
import com.joinlivora.backend.presence.repository.CreatorPresenceRepository;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CreatorPresenceService {

    private final CreatorPresenceRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OnlineCreatorRegistry onlineCreatorRegistry;
    private final LivestreamSessionRepository livestreamSessionRepository;
    private final CreatorRepository creatorRepository;
    private static final long OFFLINE_THRESHOLD_SECONDS = 60;

    public CreatorPresenceService(
            CreatorPresenceRepository repository,
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            OnlineCreatorRegistry onlineCreatorRegistry,
            LivestreamSessionRepository livestreamSessionRepository,
            CreatorRepository creatorRepository) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.onlineCreatorRegistry = onlineCreatorRegistry;
        this.livestreamSessionRepository = livestreamSessionRepository;
        this.creatorRepository = creatorRepository;
    }

    private static final String REDIS_KEY_PREFIX = "creator:presence:";

    public List<CreatorPresence> getOnlineCreators() {
        Instant threshold = Instant.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
        return repository.findByOnlineTrue().stream()
                .filter(p -> p.getLastSeen().isAfter(threshold))
                .toList();
    }

    public List<com.joinlivora.backend.creator.dto.OnlineCreatorDto> getAllOnlineCreators() {
        Instant threshold = Instant.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
        return repository.findAllOnlineCreators().stream()
                .filter(dto -> dto.isOnline() && dto.getLastSeen() != null && dto.getLastSeen().isAfter(threshold))
                .toList();
    }

    @Transactional
    public void markOnline(Long creatorId) {
        updatePresence(creatorId, true);
    }

    @Transactional
    public void markOffline(Long creatorId) {
        updatePresence(creatorId, false);
    }

    @Transactional
    public void updatePresence(Long creatorId, boolean online) {
        CreatorPresence presence = repository.findByCreatorId(creatorId)
                .orElse(CreatorPresence.builder().creatorId(creatorId).build());

        presence.setOnline(online);
        presence.setLastSeen(Instant.now());

        repository.save(presence);

        try {
            if (isRedisAvailable()) {
                String key = getRedisKey(creatorId);
                if (online) {
                    redisTemplate.opsForValue().set(key, presence, Duration.ofSeconds(120));
                } else {
                    redisTemplate.delete(key);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update creator presence in Redis: {}", e.getMessage());
        }
    }

    public boolean isOnline(Long creatorId) {
        return getAvailabilityByCreatorId(creatorId) != CreatorAvailabilityStatus.OFFLINE;
    }

    /**
     * Resolves the current availability status for a creator by their internal creator.
     */
    public CreatorAvailabilityStatus getAvailabilityByCreatorId(Long creatorId) {
        if (creatorId == null) return CreatorAvailabilityStatus.OFFLINE;
        return resolveAvailability(null, creatorId);
    }

    /**
     * Resolves the current availability status for a creator.
     * 1. LIVE if there is an active stream.
     * 2. ONLINE if there is an active WebSocket connection.
     * 3. OFFLINE otherwise.
     */
    public CreatorAvailabilityStatus getAvailability(Long creatorUserId) {
        if (creatorUserId == null) return CreatorAvailabilityStatus.OFFLINE;
        return resolveAvailability(creatorUserId, null);
    }

    private CreatorAvailabilityStatus resolveAvailability(Long creatorUserId, Long creatorId) {
        // 1. Resolve userId if missing (needed for live stream check)
        if (creatorUserId == null && creatorId != null) {
            creatorUserId = creatorRepository.findById(creatorId)
                    .map(com.joinlivora.backend.creator.model.Creator::getUser)
                    .map(com.joinlivora.backend.user.User::getId)
                    .orElse(null);
        }

        if (creatorUserId == null) return CreatorAvailabilityStatus.OFFLINE;

        // 2. Check for active live stream (LIVE status) via the new aggregate
        if (livestreamSessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(creatorUserId, LivestreamStatus.LIVE).isPresent()) {
            return CreatorAvailabilityStatus.LIVE;
        }

        // 3. Resolve internal creator if missing (needed for WebSocket check)
        if (creatorId == null) {
            creatorId = creatorRepository.findByUser_Id(creatorUserId)
                    .map(com.joinlivora.backend.creator.model.Creator::getId)
                    .orElse(null);
        }

        // 4. Check for active WebSocket connection (ONLINE status)
        if (creatorId != null && onlineCreatorRegistry.isOnline(creatorId)) {
            return CreatorAvailabilityStatus.ONLINE;
        }

        // 5. Final fallback: Check database lastSeen threshold (handles recent disconnects or cluster-wide presence)
        if (creatorId != null) {
            boolean recentlySeen = repository.findByCreatorId(creatorId)
                    .map(p -> p.isOnline() && p.getLastSeen() != null && 
                              p.getLastSeen().isAfter(Instant.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS)))
                    .orElse(false);
            if (recentlySeen) {
                return CreatorAvailabilityStatus.ONLINE;
            }
        }

        return CreatorAvailabilityStatus.OFFLINE;
    }

    @Transactional
    public void refreshLastSeen(Long creatorId) {
        repository.findByCreatorId(creatorId).ifPresent(p -> {
            p.setLastSeen(Instant.now());
            repository.save(p);
            
            try {
                if (isRedisAvailable()) {
                    redisTemplate.opsForValue().set(getRedisKey(creatorId), p, Duration.ofSeconds(120));
                }
            } catch (Exception e) {
                log.warn("Failed to update creator presence in Redis during refresh: {}", e.getMessage());
            }
        });
    }

    public Optional<CreatorPresence> getPresence(Long creatorId) {
        return repository.findByCreatorId(creatorId).map(p -> {
            if (p.isOnline() && p.getLastSeen().isBefore(Instant.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS))) {
                p.setOnline(false);
            }
            return p;
        });
    }

    private String getRedisKey(Long creatorId) {
        return REDIS_KEY_PREFIX + creatorId;
    }

    private boolean isRedisAvailable() {
        try {
            return redisTemplate != null && redisTemplate.getConnectionFactory() != null;
        } catch (Exception e) {
            return false;
        }
    }
}

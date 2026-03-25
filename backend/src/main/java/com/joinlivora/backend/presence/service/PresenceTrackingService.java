package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.creator.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking online/offline status of users and creators.
 * Responsibilities:
 * - mark user online
 * - mark user offline
 * - heartbeat handling
 */
@Service
@Slf4j
public class PresenceTrackingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OnlineStatusService onlineStatusService;
    
    private static final String ONLINE_USERS_KEY = "online_users";
    private static final String USER_SESSION_COUNT_PREFIX = "user_session_count:";

    // Set of currently connected creator entity IDs for heartbeat refresh
    private final Set<Long> activeCreatorIds = ConcurrentHashMap.newKeySet();

    // In-memory fallback ONLY for session counting when RedisTemplate is not available
    private final Map<Long, Integer> userSessionCount = new ConcurrentHashMap<>();

    public PresenceTrackingService(RedisTemplate<String, Object> redisTemplate, OnlineStatusService onlineStatusService) {
        this.redisTemplate = redisTemplate;
        this.onlineStatusService = onlineStatusService;
    }

    public void markUserOnline(Long userId) {
        if (userId == null) return;
        if (redisTemplate != null) {
            String countKey = USER_SESSION_COUNT_PREFIX + userId;
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofHours(24));
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
        } else {
            userSessionCount.merge(userId, 1, Integer::sum);
        }
    }

    /**
     * @return true if it was the last session for the user
     */
    public boolean markUserOffline(Long userId) {
        if (userId == null) return false;
        if (redisTemplate != null) {
            String countKey = USER_SESSION_COUNT_PREFIX + userId;
            Long count = redisTemplate.opsForValue().decrement(countKey);
            if (count == null || count <= 0) {
                redisTemplate.delete(countKey);
                redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
                return true;
            }
        } else {
            Integer count = userSessionCount.computeIfPresent(userId, (k, v) -> v > 1 ? v - 1 : null);
            if (count == null) {
                userSessionCount.remove(userId);
                return true;
            }
        }
        return false;
    }

    public void markCreatorOnline(Long creatorId) {
        if (creatorId != null) {
            activeCreatorIds.add(creatorId);
            if (onlineStatusService != null) {
                onlineStatusService.setOnline(creatorId);
            }
        }
    }

    public void markCreatorOffline(Long creatorId) {
        if (creatorId != null) {
            activeCreatorIds.remove(creatorId);
            if (onlineStatusService != null) {
                onlineStatusService.setOffline(creatorId);
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        if (userId == null) return false;
        if (redisTemplate != null) {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId));
        } else {
            return userSessionCount.containsKey(userId);
        }
    }

    public long getOnlineUsersCount() {
        if (redisTemplate != null) {
            Long size = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
            return size != null ? size : 0;
        } else {
            return userSessionCount.size();
        }
    }
    
    public Set<Long> getActiveCreatorIds() {
        return activeCreatorIds;
    }

    public void refreshPresence(Set<Long> userIds) {
        if (redisTemplate != null) {
            activeCreatorIds.forEach(creatorId -> {
                if (onlineStatusService != null) onlineStatusService.refreshOnlineStatus(creatorId);
            });
            
            userIds.forEach(userId -> {
                String countKey = USER_SESSION_COUNT_PREFIX + userId;
                redisTemplate.expire(countKey, Duration.ofHours(24));
                redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
            });
        }
    }

    public void resetUserCount(Long userId) {
        if (userId == null) return;
        if (redisTemplate != null) {
            redisTemplate.delete(USER_SESSION_COUNT_PREFIX + userId);
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
        } else {
            userSessionCount.remove(userId);
        }
    }

    public long getRecentNewAccountJoinCount(Long creatorUserId, Duration duration) {
        if (redisTemplate == null || creatorUserId == null) return 0;
        String key = "presence:join-cluster:" + creatorUserId;
        long now = System.currentTimeMillis();
        // Remove old joins outside the window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, (double) (now - duration.toMillis()));
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    public void trackNewAccountJoin(Long creatorUserId, Long userId, UUID streamId) {
        if (redisTemplate == null || creatorUserId == null || userId == null) return;
        String clusterKey = "presence:join-cluster:" + (streamId != null ? streamId : creatorUserId);
        redisTemplate.opsForZSet().add(clusterKey, userId.toString(), (double) System.currentTimeMillis());
        redisTemplate.expire(clusterKey, Duration.ofMinutes(15));
    }
}

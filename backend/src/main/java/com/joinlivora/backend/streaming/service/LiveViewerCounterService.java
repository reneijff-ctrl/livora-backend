package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.security.HashUtil;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class LiveViewerCounterService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final LivestreamAnalyticsService analyticsService;
    
    public static final String KEY_PREFIX = "stream:";
    /** New UUID-based active stream pointer: livestream:active-stream:{creatorId} → streamId (UUID) */
    public static final String ACTIVE_STREAM_UUID_PREFIX = "livestream:active-stream:";
    
    private final Set<Long> pendingBroadcastCreators = ConcurrentHashMap.newKeySet();

    // Metric: track UUID-based reads
    private final AtomicLong metricUuidReads = new AtomicLong(0);

    public LiveViewerCounterService(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            LivestreamAnalyticsService analyticsService
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.analyticsService = analyticsService;
    }

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ADD_VIEWER_SCRIPT = RedisScript.of(
            "local setKey = KEYS[1]; " +
            "local userHashKey = KEYS[2]; " +
            "local sessionId = ARGV[1]; " +
            "local userId = ARGV[2]; " +
            "local ip = ARGV[3]; " +
            "local ttl = tonumber(ARGV[4]); " +
            "local streamId = ARGV[5]; " +
            "local deviceHash = ARGV[6]; " +
            "local added = 0; " +
            "if userId ~= nil and userId ~= '' then " +
            "  local prevSession = redis.call('HGET', userHashKey, userId); " +
            "  if prevSession and prevSession ~= sessionId then " +
            "    redis.call('SREM', setKey, prevSession); " +
            "  end " +
            "  redis.call('HSET', userHashKey, userId, sessionId); " +
            "  added = redis.call('SADD', setKey, sessionId); " +
            "  redis.call('EXPIRE', userHashKey, ttl); " +
            "elseif ip ~= nil and ip ~= '' then " +
            "  local deviceKey = 'ip:' .. ip .. ':' .. deviceHash; " +
            "  local deviceSessionKey = 'stream:' .. streamId .. ':deviceSessions:' .. ip .. ':' .. deviceHash; " +
            "  local ipCountKey = 'stream:' .. streamId .. ':ipCounts:' .. ip; " +
            "  local isMember = redis.call('SISMEMBER', setKey, deviceKey); " +
            "  if isMember == 1 then " +
            "    redis.call('INCR', deviceSessionKey); " +
            "    redis.call('EXPIRE', deviceSessionKey, ttl); " +
            "    added = 0; " +
            "  else " +
            "    local currentIpCount = tonumber(redis.call('GET', ipCountKey) or '0'); " +
            "    if currentIpCount < 10 then " +
            "      redis.call('INCR', ipCountKey); " +
            "      redis.call('EXPIRE', ipCountKey, ttl); " +
            "      redis.call('INCR', deviceSessionKey); " +
            "      redis.call('EXPIRE', deviceSessionKey, ttl); " +
            "      added = redis.call('SADD', setKey, deviceKey); " +
            "    else " +
            "      added = 0; " +
            "    end " +
            "  end " +
            "else " +
            "  added = redis.call('SADD', setKey, sessionId); " +
            "end " +
            "redis.call('EXPIRE', setKey, ttl); " +
            "local totalCount = redis.call('SCARD', setKey); " +
            "return {added, totalCount};", List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> REMOVE_VIEWER_SCRIPT = RedisScript.of(
            "local setKey = KEYS[1]; " +
            "local userHashKey = KEYS[2]; " +
            "local sessionId = ARGV[1]; " +
            "local userId = ARGV[2]; " +
            "local ip = ARGV[3]; " +
            "local streamId = ARGV[4]; " +
            "local deviceHash = ARGV[5]; " +
            "local removed = 0; " +
            "if userId ~= nil and userId ~= '' then " +
            "  local currentHashSession = redis.call('HGET', userHashKey, userId); " +
            "  if sessionId == currentHashSession then " +
            "    redis.call('HDEL', userHashKey, userId); " +
            "  end " +
            "  removed = redis.call('SREM', setKey, sessionId); " +
            "elseif ip ~= nil and ip ~= '' then " +
            "  local deviceKey = 'ip:' .. ip .. ':' .. deviceHash; " +
            "  local deviceSessionKey = 'stream:' .. streamId .. ':deviceSessions:' .. ip .. ':' .. deviceHash; " +
            "  local ipCountKey = 'stream:' .. streamId .. ':ipCounts:' .. ip; " +
            "  if redis.call('SISMEMBER', setKey, deviceKey) == 1 then " +
            "    local count = redis.call('DECR', deviceSessionKey); " +
            "    if count <= 0 then " +
            "      redis.call('DEL', deviceSessionKey); " +
            "      local ipCount = redis.call('DECR', ipCountKey); " +
            "      if ipCount <= 0 then redis.call('DEL', ipCountKey) end " +
            "      removed = redis.call('SREM', setKey, deviceKey); " +
            "    else " +
            "      removed = 0; " +
            "    end " +
            "  else " +
            "    removed = 0; " +
            "  end " +
            "else " +
            "  removed = redis.call('SREM', setKey, sessionId); " +
            "end " +
            "local totalCount = redis.call('SCARD', setKey); " +
            "return {removed, totalCount};", List.class);

    // -------------------------------------------------------------------------
    // UUID-based API (new — Stream as single source of truth)
    // -------------------------------------------------------------------------

    /**
     * Adds a viewer using Stream UUID as the Redis key namespace.
     * Key pattern: stream:{streamId}:viewers, stream:{streamId}:userViewers
     */
    public void addViewer(UUID streamId, Long creatorUserId, Long viewerUserId, String sessionId, String ip, String userAgent) {
        if (streamId == null || creatorUserId == null || sessionId == null) return;

        setActiveStreamUuid(creatorUserId, streamId);

        String key = KEY_PREFIX + streamId + ":viewers";
        String userMapKey = KEY_PREFIX + streamId + ":userViewers";

        String userIdStr = viewerUserId != null ? String.valueOf(viewerUserId) : "";
        String ipStr = ip != null ? ip : "";
        String deviceHash = HashUtil.sha256(userAgent != null ? userAgent : "unknown");

        @SuppressWarnings("unchecked")
        List<Long> results = redisTemplate.execute(ADD_VIEWER_SCRIPT,
                Arrays.asList(key, userMapKey),
                sessionId, userIdStr, ipStr, "1800", streamId.toString(), deviceHash);

        if (results != null && results.size() >= 2) {
            Long added = results.get(0);
            Long count = results.get(1);
            if (added != null && added > 0) {
                log.info("VIEWER_JOIN: streamId={}, creatorUserId={}, viewerUserId={}, sessionId={}", streamId, creatorUserId, viewerUserId, sessionId);
                log.info("VIEWER_COUNT_UPDATE: streamId={}, count={}", streamId, count);
                analyticsService.onViewerIncrement(creatorUserId, count);
                pendingBroadcastCreators.add(creatorUserId);
            }
        }
    }

    /**
     * Removes a viewer using Stream UUID as the Redis key namespace.
     */
    public void removeViewer(UUID streamId, Long creatorUserId, Long viewerUserId, String sessionId, String ip, String userAgent) {
        if (streamId == null || creatorUserId == null || sessionId == null) return;
        String key = KEY_PREFIX + streamId + ":viewers";
        String userMapKey = KEY_PREFIX + streamId + ":userViewers";

        String userIdStr = viewerUserId != null ? String.valueOf(viewerUserId) : "";
        String ipStr = ip != null ? ip : "";
        String deviceHash = HashUtil.sha256(userAgent != null ? userAgent : "unknown");

        @SuppressWarnings("unchecked")
        List<Long> results = redisTemplate.execute(REMOVE_VIEWER_SCRIPT,
                Arrays.asList(key, userMapKey),
                sessionId, userIdStr, ipStr, streamId.toString(), deviceHash);

        if (results != null && results.size() >= 1) {
            Long removed = results.get(0);
            if (removed != null && removed > 0) {
                Long count = results.size() >= 2 ? results.get(1) : 0L;
                log.info("VIEWER_LEAVE: streamId={}, creatorUserId={}, viewerUserId={}, sessionId={}", streamId, creatorUserId, viewerUserId, sessionId);
                log.info("VIEWER_COUNT_UPDATE: streamId={}, count={}", streamId, count);
                pendingBroadcastCreators.add(creatorUserId);
            }
        }
    }

    public UUID getActiveStreamUuid(Long creatorUserId) {
        if (creatorUserId == null) return null;
        String id = redisTemplate.opsForValue().get(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        return id != null ? UUID.fromString(id) : null;
    }

    public void setActiveStreamUuid(Long creatorUserId, UUID streamId) {
        if (creatorUserId == null) return;
        if (streamId == null) {
            redisTemplate.delete(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        } else {
            redisTemplate.opsForValue().set(ACTIVE_STREAM_UUID_PREFIX + creatorUserId, streamId.toString(), java.time.Duration.ofMinutes(30));
        }
    }

    /**
     * Resets viewer count keys for a Stream UUID (new format).
     * Also cleans up the UUID-based active stream pointer.
     */
    public void resetViewerCountByStreamId(UUID streamId, Long creatorUserId) {
        if (streamId == null) return;

        String pattern = KEY_PREFIX + streamId + ":*";
        try {
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                org.springframework.data.redis.core.ScanOptions options = org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(options)) {
                    List<byte[]> keysToDelete = new ArrayList<>();
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                        if (keysToDelete.size() >= 100) {
                            connection.del(keysToDelete.toArray(new byte[0][]));
                            keysToDelete.clear();
                        }
                    }
                    if (!keysToDelete.isEmpty()) {
                        connection.del(keysToDelete.toArray(new byte[0][]));
                    }
                } catch (Exception e) {
                    log.error("Error during scan/delete for streamId {}: {}", streamId, e.getMessage());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to safely clean up Redis keys for streamId {}: {}", streamId, e.getMessage());
        }

        if (creatorUserId != null) {
            redisTemplate.delete(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        }
        log.info("LIVESTREAM: Reset viewer count keys for streamId={}, creatorUserId={}", streamId, creatorUserId);
    }


    @Scheduled(fixedRate = 3000)
    public void processThrottledBroadcasts() {
        if (pendingBroadcastCreators.isEmpty()) return;
        
        Set<Long> creatorsToUpdate = new HashSet<>(pendingBroadcastCreators);
        pendingBroadcastCreators.clear();
        
        for (Long creatorId : creatorsToUpdate) {
            try {
                long currentCount = getViewerCount(creatorId);
                broadcastUpdate(creatorId, currentCount);
            } catch (Exception e) {
                log.error("Failed to broadcast throttled viewer count for creator {}: {}", creatorId, e.getMessage());
            }
        }
    }

    public void syncToPostgresForce(Long creatorUserId, long count) {
        // No longer used
    }

    public void batchSyncToPostgres(Map<Long, Integer> viewerCounts) {
        // No longer used
    }

    public long getViewerCount(Long creatorUserId) {
        if (creatorUserId == null) return 0;

        String uuidStr = redisTemplate.opsForValue().get(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        if (uuidStr != null) {
            String key = KEY_PREFIX + uuidStr + ":viewers";
            Long count = redisTemplate.opsForSet().size(key);
            if (count != null && count > 0) {
                metricUuidReads.incrementAndGet();
                return count;
            }
        }
        return 0L;
    }

    public void recordViewerHistory(UUID streamId, long viewerCount) {
        if (streamId == null) return;
        String key = "viewer-history:" + streamId;
        long timestamp = System.currentTimeMillis();
        
        // Use ZSET: score = timestamp, member = viewerCount
        redisTemplate.opsForZSet().add(key, String.valueOf(viewerCount), (double) timestamp);
        
        // Keep only the last 5 entries (about 2.5 minutes if polled every 30s)
        redisTemplate.opsForZSet().removeRange(key, 0, -6);
        
        // Set TTL to 1 hour to avoid orphaned keys
        redisTemplate.expire(key, java.time.Duration.ofHours(1));
    }

    public Long getPreviousViewerCount(UUID streamId) {
        if (streamId == null) return null;
        String key = "viewer-history:" + streamId;
        // Get the 2nd newest element (index 1 in reverse order)
        Set<String> history = redisTemplate.opsForZSet().reverseRange(key, 1, 1);
        if (history != null && !history.isEmpty()) {
            try {
                return Long.valueOf(history.iterator().next());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public java.util.Set<String> getViewers(Long creatorUserId) {
        if (creatorUserId == null) return java.util.Collections.emptySet();

        String uuidStr = redisTemplate.opsForValue().get(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        if (uuidStr != null) {
            String key = KEY_PREFIX + uuidStr + ":viewers";
            Set<String> members = redisTemplate.opsForSet().members(key);
            if (members != null && !members.isEmpty()) {
                metricUuidReads.incrementAndGet();
                return members;
            }
        }
        return java.util.Collections.emptySet();
    }

    /**
     * Returns the set of authenticated user IDs currently viewing a creator's stream,
     * read directly from the Redis userViewers hash (userId -> wsSessionId mapping).
     */
    public java.util.Set<Long> getAuthenticatedViewerUserIds(Long creatorUserId) {
        if (creatorUserId == null) return java.util.Collections.emptySet();

        String uuidStr = redisTemplate.opsForValue().get(ACTIVE_STREAM_UUID_PREFIX + creatorUserId);
        if (uuidStr == null) {
            log.debug("VIEWER_LIST_DEBUG: No active stream UUID for creatorUserId={}", creatorUserId);
            return java.util.Collections.emptySet();
        }
        String resolvedKey = KEY_PREFIX + uuidStr + ":userViewers";
        metricUuidReads.incrementAndGet();

        String userMapKey = resolvedKey;
        log.debug("VIEWER_LIST_DEBUG: Reading hash key={}", resolvedKey);

        // Use low-level connection to read HKEYS directly, avoiding potential
        // serializer mismatch when StringRedisTemplate reads Lua-written hash keys
        Set<Long> userIds = new HashSet<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(userMapKey);
            log.debug("VIEWER_LIST_DEBUG: Hash entries count={}, raw={}", entries.size(), entries);
            for (Object rawKey : entries.keySet()) {
                try {
                    userIds.add(Long.valueOf(rawKey.toString()));
                } catch (NumberFormatException e) {
                    log.warn("VIEWER_LIST_DEBUG: Non-numeric hash key: '{}' (class={})", rawKey, rawKey.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.error("VIEWER_LIST_DEBUG: Failed to read userViewers hash: {}", e.getMessage());
        }

        log.debug("VIEWER_LIST_DEBUG: Resolved userIds={} for creatorUserId={}", userIds, creatorUserId);
        return userIds;
    }

    /**
     * Returns a snapshot of Redis key usage metrics for monitoring.
     */
    public Map<String, Long> getRedisKeyMetrics() {
        return Map.of(
                "uuidReads", metricUuidReads.get()
        );
    }

    public void reconcileCount(Long creatorUserId, long groundTruthCount) {
        if (creatorUserId == null) return;
        log.info("LIVESTREAM: Reconciling count for creatorUserId={} to ground-truth={}", creatorUserId, groundTruthCount);
        broadcastUpdate(creatorUserId, groundTruthCount);
    }

    private void broadcastUpdate(Long creatorUserId, long currentCount) {
        log.info("VIEWER_COUNT_UPDATE: creatorUserId={}, currentCount={}", creatorUserId, currentCount);
        RealtimeMessage message = RealtimeMessage.of("viewer_count:update", Map.of(
            "creatorUserId", creatorUserId,
            "viewerCount", currentCount
        ));
        messagingTemplate.convertAndSend("/exchange/amq.topic/viewers." + creatorUserId, message);
    }
}

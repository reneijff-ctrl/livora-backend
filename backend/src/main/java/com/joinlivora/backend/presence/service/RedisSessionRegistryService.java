package com.joinlivora.backend.presence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-backed session registry that replaces the in-memory ConcurrentHashMap-based
 * SessionRegistryService. Stores all WebSocket session metadata in Redis for
 * multi-instance scalability and automatic TTL-based cleanup of stale sessions.
 *
 * Redis key design:
 *   ws:session:{sessionId}              HASH  → principal, userId, creatorId, ip, userAgent, registeredAt
 *   ws:session:{sessionId}:subs         SET   → destination strings
 *   ws:session:{sessionId}:submap       HASH  → subscriptionId → destination
 *   ws:session:{sessionId}:jointimes    HASH  → destination → ISO-8601 instant
 *   ws:session:{sessionId}:streams      SET   → streamSessionId strings
 *   ws:user:{userId}:sessions           SET   → sessionId strings (reverse index)
 *   ws:sessions:active                  SET   → all active sessionId strings (replaces KEYS scan)
 *
 * All session keys share the same TTL (5 minutes), refreshed every heartbeat (30s).
 * Reverse indexes are cleaned on unregister + periodic reconciliation.
 * Active session tracking uses an explicit SET instead of KEYS to avoid O(N) blocking.
 */
@Service
@Slf4j
public class RedisSessionRegistryService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(5);
    private static final String SESSION_PREFIX = "ws:session:";
    private static final String USER_SESSIONS_PREFIX = "ws:user:";
    private static final String ACTIVE_SESSIONS_SET = "ws:sessions:active";

    private final StringRedisTemplate redis;

    public RedisSessionRegistryService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ── Registration ──────────────────────────────────────────────────────

    public void registerSession(String sessionId, String principalName,
                                Long userId, Long creatorId,
                                String ip, String userAgent) {
        String key = sessionKey(sessionId);
        Map<String, String> fields = new HashMap<>();
        if (principalName != null) fields.put("principal", principalName);
        if (userId != null) fields.put("userId", userId.toString());
        if (creatorId != null) fields.put("creatorId", creatorId.toString());
        if (ip != null) fields.put("ip", ip);
        if (userAgent != null) fields.put("userAgent", userAgent);
        fields.put("registeredAt", Instant.now().toString());

        redis.opsForHash().putAll(key, fields);
        redis.expire(key, SESSION_TTL);

        // Track in active sessions set
        redis.opsForSet().add(ACTIVE_SESSIONS_SET, sessionId);

        // Reverse index: userId → sessions
        if (userId != null) {
            redis.opsForSet().add(userSessionsKey(userId), sessionId);
        }

        log.debug("Session registered in Redis: {}", sessionId);
    }

    public void unregisterSession(String sessionId) {
        String key = sessionKey(sessionId);

        // Read metadata before deleting for reverse index cleanup
        String userIdStr = (String) redis.opsForHash().get(key, "userId");

        // Collect all keys to delete
        List<String> keysToDelete = List.of(
                key,
                subsKey(sessionId),
                submapKey(sessionId),
                joinTimesKey(sessionId),
                streamsKey(sessionId)
        );
        redis.delete(keysToDelete);

        // Remove from active sessions set
        redis.opsForSet().remove(ACTIVE_SESSIONS_SET, sessionId);

        // Clean reverse index
        if (userIdStr != null) {
            redis.opsForSet().remove(userSessionsKey(Long.valueOf(userIdStr)), sessionId);
        }

        log.debug("Session unregistered from Redis: {}", sessionId);
    }

    // ── Simple Getters ────────────────────────────────────────────────────

    public String getPrincipal(String sessionId) {
        return (String) redis.opsForHash().get(sessionKey(sessionId), "principal");
    }

    public Long getUserId(String sessionId) {
        String val = (String) redis.opsForHash().get(sessionKey(sessionId), "userId");
        return val != null ? Long.valueOf(val) : null;
    }

    public Long getCreatorId(String sessionId) {
        String val = (String) redis.opsForHash().get(sessionKey(sessionId), "creatorId");
        return val != null ? Long.valueOf(val) : null;
    }

    public String getIp(String sessionId) {
        return (String) redis.opsForHash().get(sessionKey(sessionId), "ip");
    }

    public String getUserAgent(String sessionId) {
        return (String) redis.opsForHash().get(sessionKey(sessionId), "userAgent");
    }

    // ── Subscriptions ─────────────────────────────────────────────────────

    public boolean addSubscription(String sessionId, String subscriptionId, String destination) {
        // Track subscriptionId → destination mapping
        redis.opsForHash().put(submapKey(sessionId), subscriptionId, destination);
        refreshSubKeys(sessionId);

        // Add destination to the set; return true if it was newly added
        Long added = redis.opsForSet().add(subsKey(sessionId), destination);
        redis.expire(subsKey(sessionId), SESSION_TTL);
        return added != null && added > 0;
    }

    public String removeSubscription(String sessionId, String subscriptionId) {
        String destination = (String) redis.opsForHash().get(submapKey(sessionId), subscriptionId);
        if (destination != null) {
            redis.opsForHash().delete(submapKey(sessionId), subscriptionId);
            redis.opsForSet().remove(subsKey(sessionId), destination);
            return destination;
        }
        return null;
    }

    public boolean isSubscribedTo(String sessionId, String destination) {
        Boolean member = redis.opsForSet().isMember(subsKey(sessionId), destination);
        return Boolean.TRUE.equals(member);
    }

    public Set<String> getSubscriptions(String sessionId) {
        Set<String> members = redis.opsForSet().members(subsKey(sessionId));
        return members != null ? members : Collections.emptySet();
    }

    // ── Join Times ────────────────────────────────────────────────────────

    public void trackJoinTime(String sessionId, String destination, Instant joinTime) {
        redis.opsForHash().put(joinTimesKey(sessionId), destination, joinTime.toString());
        redis.expire(joinTimesKey(sessionId), SESSION_TTL);
    }

    public Instant getJoinTime(String sessionId, String destination) {
        String val = (String) redis.opsForHash().get(joinTimesKey(sessionId), destination);
        return val != null ? Instant.parse(val) : null;
    }

    public Instant removeJoinTime(String sessionId, String destination) {
        String val = (String) redis.opsForHash().get(joinTimesKey(sessionId), destination);
        if (val != null) {
            redis.opsForHash().delete(joinTimesKey(sessionId), destination);
            return Instant.parse(val);
        }
        return null;
    }

    // ── Joined Streams ────────────────────────────────────────────────────

    public boolean markStreamJoined(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        Long added = redis.opsForSet().add(streamsKey(sessionId), streamSessionId.toString());
        redis.expire(streamsKey(sessionId), SESSION_TTL);
        return added != null && added > 0;
    }

    public boolean markStreamLeft(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        Long removed = redis.opsForSet().remove(streamsKey(sessionId), streamSessionId.toString());
        return removed != null && removed > 0;
    }

    public Set<Long> getJoinedStreams(String sessionId) {
        Set<String> members = redis.opsForSet().members(streamsKey(sessionId));
        if (members == null || members.isEmpty()) return Collections.emptySet();
        return members.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    // ── Active Sessions ───────────────────────────────────────────────────

    /**
     * Returns a map of sessionId → principal for all sessions that still exist in Redis.
     * Uses the explicit active sessions SET to discover sessions (non-blocking, replaces KEYS).
     *
     * Note: In a multi-instance setup this returns ALL active sessions across all instances,
     * unlike the old in-memory version which only saw local sessions.
     */
    public Map<String, String> getAllActiveSessions() {
        Map<String, String> result = new HashMap<>();
        Set<String> sessionIds = redis.opsForSet().members(ACTIVE_SESSIONS_SET);
        if (sessionIds == null) return result;

        for (String sessionId : sessionIds) {
            String key = sessionKey(sessionId);
            // Validate session still exists (TTL may have expired)
            String principal = (String) redis.opsForHash().get(key, "principal");
            if (principal != null) {
                result.put(sessionId, principal);
            } else {
                // Stale entry — session expired but SET wasn't cleaned; remove lazily
                redis.opsForSet().remove(ACTIVE_SESSIONS_SET, sessionId);
            }
        }
        return result;
    }

    /**
     * Returns the count of active sessions using SCARD on the active sessions SET.
     * O(1) operation — no KEYS scan required.
     */
    public long getActiveSessionsCount() {
        Long size = redis.opsForSet().size(ACTIVE_SESSIONS_SET);
        return size != null ? size : 0;
    }

    // ── TTL Management ────────────────────────────────────────────────────

    /**
     * Refreshes TTL on all keys for a given session. Called by the heartbeat scheduler
     * (typically every 30 seconds) to keep active sessions alive.
     *
     * @return true if the session still exists, false if it has already expired
     */
    public boolean refreshSession(String sessionId) {
        String key = sessionKey(sessionId);
        Boolean exists = redis.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            return false;
        }

        redis.expire(key, SESSION_TTL);
        redis.expire(subsKey(sessionId), SESSION_TTL);
        redis.expire(submapKey(sessionId), SESSION_TTL);
        redis.expire(joinTimesKey(sessionId), SESSION_TTL);
        redis.expire(streamsKey(sessionId), SESSION_TTL);
        return true;
    }

    // ── Reverse Index Reconciliation ──────────────────────────────────────

    /**
     * Cleans stale entries from user→sessions reverse indexes.
     * Uses SCAN instead of KEYS to avoid blocking Redis.
     * Should be called periodically (e.g. every 5 minutes via @Scheduled).
     */
    public void reconcileReverseIndexes() {
        int cleaned = 0;

        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(USER_SESSIONS_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redis.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String userSessionsKey = cursor.next();

                Set<String> sessionIds = redis.opsForSet().members(userSessionsKey);
                if (sessionIds == null) continue;

                for (String sid : sessionIds) {
                    if (!Boolean.TRUE.equals(redis.hasKey(sessionKey(sid)))) {
                        redis.opsForSet().remove(userSessionsKey, sid);
                        cleaned++;
                    }
                }

                // Remove empty reverse index keys
                Long size = redis.opsForSet().size(userSessionsKey);
                if (size != null && size == 0) {
                    redis.delete(userSessionsKey);
                }
            }
        } catch (Exception e) {
            log.error("Error during reverse index reconciliation: {}", e.getMessage());
        }

        // Also reconcile the active sessions SET — remove entries for expired sessions
        Set<String> activeSessionIds = redis.opsForSet().members(ACTIVE_SESSIONS_SET);
        if (activeSessionIds != null) {
            int staleActive = 0;
            for (String sid : activeSessionIds) {
                if (!Boolean.TRUE.equals(redis.hasKey(sessionKey(sid)))) {
                    redis.opsForSet().remove(ACTIVE_SESSIONS_SET, sid);
                    staleActive++;
                }
            }
            if (staleActive > 0) {
                log.info("Reconciled {} stale entries from active sessions set", staleActive);
            }
        }

        if (cleaned > 0) {
            log.info("Reconciled {} stale entries from user session reverse indexes", cleaned);
        }
    }

    // ── Key Helpers ───────────────────────────────────────────────────────

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String subsKey(String sessionId) {
        return SESSION_PREFIX + sessionId + ":subs";
    }

    private String submapKey(String sessionId) {
        return SESSION_PREFIX + sessionId + ":submap";
    }

    private String joinTimesKey(String sessionId) {
        return SESSION_PREFIX + sessionId + ":jointimes";
    }

    private String streamsKey(String sessionId) {
        return SESSION_PREFIX + sessionId + ":streams";
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_PREFIX + userId + ":sessions";
    }

    private void refreshSubKeys(String sessionId) {
        redis.expire(submapKey(sessionId), SESSION_TTL);
    }
}

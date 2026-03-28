package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages per-creator-room moderator assignments using Redis.
 * Supports two persistence modes:
 *   - persistent: survives across streams (stream:{creatorId}:moderators:persistent)
 *   - stream-only: cleared when the stream ends (stream:{creatorId}:moderators:session)
 *
 * A user is considered a moderator if they are in EITHER set.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StreamModeratorService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private static final String PERSISTENT_KEY = "stream:%s:moderators:persistent";
    private static final String SESSION_KEY = "stream:%s:moderators:session";
    /** Legacy key from Phase 1 — migrated reads only */
    private static final String LEGACY_KEY = "stream:%s:moderators";

    // ── Grant / Revoke ──────────────────────────────────────────────

    /**
     * Grant moderator privileges.
     * @param persistent true → survives across streams; false → cleared on stream end
     */
    public void grantModerator(Long creatorId, Long userId, boolean persistent) {
        String key = persistent
                ? String.format(PERSISTENT_KEY, creatorId)
                : String.format(SESSION_KEY, creatorId);
        redisTemplate.opsForSet().add(key, userId.toString());
        // Also add to legacy key for backward compat during transition
        redisTemplate.opsForSet().add(String.format(LEGACY_KEY, creatorId), userId.toString());
        log.info("Granted {} moderator to user {} in creator {} room", persistent ? "persistent" : "stream-only", userId, creatorId);
    }

    /** Backward-compatible overload — defaults to persistent (Phase 1 behavior). */
    public void grantModerator(Long creatorId, Long userId) {
        grantModerator(creatorId, userId, true);
    }

    /**
     * Revoke moderator from ALL sets (persistent + session + legacy).
     */
    public void revokeModerator(Long creatorId, Long userId) {
        redisTemplate.opsForSet().remove(String.format(PERSISTENT_KEY, creatorId), userId.toString());
        redisTemplate.opsForSet().remove(String.format(SESSION_KEY, creatorId), userId.toString());
        redisTemplate.opsForSet().remove(String.format(LEGACY_KEY, creatorId), userId.toString());
        log.info("Revoked moderator from user {} in creator {} room", userId, creatorId);
    }

    // ── Query ───────────────────────────────────────────────────────

    /**
     * Check if a user is a moderator (persistent OR session OR legacy).
     */
    public boolean isModerator(Long creatorId, Long userId) {
        String uid = userId.toString();
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(String.format(PERSISTENT_KEY, creatorId), uid))
                || Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(String.format(SESSION_KEY, creatorId), uid))
                || Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(String.format(LEGACY_KEY, creatorId), uid));
    }

    /**
     * Get ALL moderator user IDs (union of persistent + session + legacy).
     */
    public Set<Long> getModeratorIds(Long creatorId) {
        Set<Long> ids = new HashSet<>();
        addMembers(ids, String.format(PERSISTENT_KEY, creatorId));
        addMembers(ids, String.format(SESSION_KEY, creatorId));
        addMembers(ids, String.format(LEGACY_KEY, creatorId));
        return ids;
    }

    private void addMembers(Set<Long> target, String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members != null) {
            members.forEach(m -> {
                try { target.add(Long.parseLong(m)); } catch (NumberFormatException ignored) {}
            });
        }
    }

    /**
     * Return moderator details (id + username + persistence type) for the creator dashboard.
     */
    public List<Map<String, Object>> getModeratorDetails(Long creatorId) {
        Set<Long> persistentIds = membersAsLongSet(String.format(PERSISTENT_KEY, creatorId));
        Set<Long> sessionIds = membersAsLongSet(String.format(SESSION_KEY, creatorId));

        Set<Long> allIds = new HashSet<>(persistentIds);
        allIds.addAll(sessionIds);

        // Also include legacy key members (treat as persistent)
        Set<Long> legacyIds = membersAsLongSet(String.format(LEGACY_KEY, creatorId));
        // Legacy members not yet in persistent/session are treated as persistent
        for (Long lid : legacyIds) {
            if (!persistentIds.contains(lid) && !sessionIds.contains(lid)) {
                persistentIds.add(lid);
                allIds.add(lid);
            }
        }

        if (allIds.isEmpty()) return Collections.emptyList();

        List<User> users = userRepository.findAllById(allIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : allIds) {
            User u = userMap.get(id);
            if (u == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", id);
            entry.put("username", u.getUsername());
            entry.put("persistent", persistentIds.contains(id));
            result.add(entry);
        }
        return result;
    }

    private Set<Long> membersAsLongSet(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) return new HashSet<>();
        Set<Long> set = new HashSet<>();
        members.forEach(m -> { try { set.add(Long.parseLong(m)); } catch (NumberFormatException ignored) {} });
        return set;
    }

    // ── Bulk operations ─────────────────────────────────────────────

    /**
     * Clear ALL moderators (persistent + session + legacy) for a creator.
     */
    public void clearAllModerators(Long creatorId) {
        redisTemplate.delete(String.format(PERSISTENT_KEY, creatorId));
        redisTemplate.delete(String.format(SESSION_KEY, creatorId));
        redisTemplate.delete(String.format(LEGACY_KEY, creatorId));
        log.info("Cleared all moderators for creator {}", creatorId);
    }

    /**
     * Clear stream-only (session) moderators for a creator.
     * Called when the creator's stream ends.
     */
    public void clearSessionModerators(Long creatorId) {
        String sessionKey = String.format(SESSION_KEY, creatorId);
        Set<String> members = redisTemplate.opsForSet().members(sessionKey);
        redisTemplate.delete(sessionKey);
        // Also remove these users from the legacy key if they are not persistent
        if (members != null && !members.isEmpty()) {
            String persistentKey = String.format(PERSISTENT_KEY, creatorId);
            String legacyKey = String.format(LEGACY_KEY, creatorId);
            for (String uid : members) {
                if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(persistentKey, uid))) {
                    redisTemplate.opsForSet().remove(legacyKey, uid);
                }
            }
        }
        log.info("Cleared session moderators for creator {}", creatorId);
    }

    // ── Broadcasts ──────────────────────────────────────────────────

    /**
     * Broadcast a bot message to the creator's live chat when a moderator is granted or revoked.
     */
    public void broadcastModeratorChange(Long creatorId, String creatorUsername, Long userId, boolean granted) {
        userRepository.findById(userId).ifPresent(user -> {
            String action = granted ? "granted moderator privileges to" : "removed moderator privileges from";
            String content = creatorUsername + " " + action + " " + user.getUsername();

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "BOT");
            payload.put("content", content);
            payload.put("senderUsername", "Livora AI");
            payload.put("senderRole", "BOT");
            payload.put("timestamp", Instant.now().toString());

            messagingTemplate.convertAndSend("/topic/stream/" + creatorId + "/chat", payload);
            log.info("Broadcast moderator change: {}", content);
        });
    }

    /**
     * Broadcast a bot message when all moderators are cleared.
     */
    public void broadcastAllModeratorsCleared(Long creatorId, String creatorUsername) {
        String content = creatorUsername + " cleared all moderator privileges";

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BOT");
        payload.put("content", content);
        payload.put("senderUsername", "Livora AI");
        payload.put("senderRole", "BOT");
        payload.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/stream/" + creatorId + "/chat", payload);
        log.info("Broadcast all moderators cleared for creator {}", creatorId);
    }

    /**
     * Broadcast a bot message when a message is removed by a moderator.
     */
    public void broadcastMessageRemoved(Long creatorId, String moderatorUsername) {
        String content = "A message was removed by " + moderatorUsername;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BOT");
        payload.put("content", content);
        payload.put("senderUsername", "Livora AI");
        payload.put("senderRole", "BOT");
        payload.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/stream/" + creatorId + "/chat", payload);
    }

    /**
     * Notify a user about their moderator status change via personal queue.
     */
    public void notifyModeratorStatusChange(Long userId, Long creatorId, boolean granted) {
        RealtimeMessage event = RealtimeMessage.builder()
                .type("MODERATOR_STATUS")
                .payload(Map.of(
                        "creatorId", creatorId,
                        "isModerator", granted
                ))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/moderation", event);
    }

    /**
     * Broadcast a MODERATOR_LIST_UPDATED event so the creator dashboard can refresh.
     */
    public void broadcastModeratorListUpdate(Long creatorId) {
        RealtimeMessage event = RealtimeMessage.builder()
                .type("MODERATOR_LIST_UPDATED")
                .payload(Map.of("creatorId", creatorId))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, event);
    }
}

package com.joinlivora.backend.moderation;

import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreatorRoomBanService {

    private final CreatorRoomBanRepository banRepository;
    private final ModerationAuditLogRepository auditRepository;
    private final UserRepository userRepository;
    private final StreamModeratorService streamModeratorService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final Map<String, Duration> BAN_DURATIONS = Map.of(
            "5m", Duration.ofMinutes(5),
            "30m", Duration.ofMinutes(30),
            "24h", Duration.ofHours(24)
    );

    // ── Ban ─────────────────────────────────────────────────────────

    @Transactional
    public CreatorRoomBan banUser(Long creatorId, Long targetUserId, Long actorUserId, String banType, String reason) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Actor user not found"));

        // Safety checks
        validateBanPermissions(creatorId, targetUserId, actorUserId, actor, targetUser, banType);

        // Deactivate any existing active ban for this user in this room
        banRepository.findActiveBan(creatorId, targetUserId, Instant.now()).ifPresent(existingBan -> {
            existingBan.setActive(false);
            banRepository.save(existingBan);
        });

        // Calculate expiry
        Instant expiresAt = null;
        if (!"permanent".equals(banType)) {
            Duration duration = BAN_DURATIONS.get(banType);
            if (duration == null) {
                throw new IllegalArgumentException("Invalid ban type: " + banType + ". Allowed: 5m, 30m, 24h, permanent");
            }
            expiresAt = Instant.now().plus(duration);
        }

        CreatorRoomBan ban = CreatorRoomBan.builder()
                .creatorId(creatorId)
                .targetUser(targetUser)
                .issuedBy(actor)
                .banType(banType)
                .reason(reason)
                .expiresAt(expiresAt)
                .build();

        ban = banRepository.save(ban);

        // Determine actor role for audit
        String actorRole = determineActorRole(creatorId, actorUserId, actor);

        // Audit log
        logAudit("BAN", creatorId, targetUserId, targetUser.getUsername(),
                actorUserId, actor.getUsername(), actorRole,
                "{\"banType\":\"" + banType + "\"" + (reason != null ? ",\"reason\":\"" + reason + "\"" : "") + "}");

        // Broadcast ban bot message to chat
        broadcastBanMessage(creatorId, targetUser.getUsername(), banType);

        // Notify the banned user directly
        notifyBannedUser(targetUserId, creatorId, banType, expiresAt);

        // Broadcast ban list update to creator dashboard
        broadcastBanListUpdate(creatorId);

        log.info("User {} banned from creator {} room by {} (type: {})", targetUserId, creatorId, actorUserId, banType);
        return ban;
    }

    // ── Unban ───────────────────────────────────────────────────────

    @Transactional
    public void unbanUser(Long creatorId, Long targetUserId, Long actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Actor user not found"));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // Only creator or admin can unban
        if (!actorUserId.equals(creatorId) && actor.getRole() != Role.ADMIN) {
            throw new SecurityException("Only the creator or admin can unban users");
        }

        List<CreatorRoomBan> activeBans = banRepository.findActiveBansByCreator(creatorId, Instant.now());
        boolean found = false;
        for (CreatorRoomBan ban : activeBans) {
            if (ban.getTargetUser().getId().equals(targetUserId)) {
                ban.setActive(false);
                banRepository.save(ban);
                found = true;
            }
        }

        if (!found) {
            throw new IllegalStateException("No active ban found for this user");
        }

        String actorRole = determineActorRole(creatorId, actorUserId, actor);

        logAudit("UNBAN", creatorId, targetUserId, targetUser.getUsername(),
                actorUserId, actor.getUsername(), actorRole, null);

        // Broadcast unban bot message
        broadcastUnbanMessage(creatorId, targetUser.getUsername());

        // Notify the unbanned user
        notifyUnbannedUser(targetUserId, creatorId);

        // Broadcast ban list update
        broadcastBanListUpdate(creatorId);

        log.info("User {} unbanned from creator {} room by {}", targetUserId, creatorId, actorUserId);
    }

    // ── Query ───────────────────────────────────────────────────────

    public boolean isUserBanned(Long creatorId, Long userId) {
        return banRepository.findActiveBan(creatorId, userId, Instant.now()).isPresent();
    }

    public List<CreatorRoomBan> getActiveBans(Long creatorId) {
        return banRepository.findActiveBansByCreator(creatorId, Instant.now());
    }

    public List<ModerationAuditLog> getAuditHistory(Long creatorId, int limit) {
        return auditRepository.findRecentByCreatorId(creatorId, limit);
    }

    public List<ModerationAuditLog> getFullAuditHistory(Long creatorId) {
        return auditRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);
    }

    // ── Audit Logging (reusable for external callers) ───────────────

    public void logAudit(String actionType, Long creatorId, Long targetUserId, String targetUsername,
                         Long actorUserId, String actorUsername, String actorRole, String metadata) {
        ModerationAuditLog entry = ModerationAuditLog.builder()
                .actionType(actionType)
                .creatorId(creatorId)
                .targetUserId(targetUserId)
                .targetUsername(targetUsername)
                .actorUserId(actorUserId)
                .actorUsername(actorUsername)
                .actorRole(actorRole)
                .metadata(metadata)
                .build();
        auditRepository.save(entry);
    }

    // ── Validation ──────────────────────────────────────────────────

    private void validateBanPermissions(Long creatorId, Long targetUserId, Long actorUserId,
                                         User actor, User targetUser, String banType) {
        // Cannot ban the creator
        if (targetUserId.equals(creatorId)) {
            throw new SecurityException("Cannot ban the room creator");
        }

        // Cannot ban admins
        if (targetUser.getRole() == Role.ADMIN) {
            throw new SecurityException("Cannot ban platform admins");
        }

        boolean isCreator = actorUserId.equals(creatorId);
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isModerator = streamModeratorService.isModerator(creatorId, actorUserId);

        if (!isCreator && !isAdmin && !isModerator) {
            throw new SecurityException("No permission to ban users in this room");
        }

        // Moderators cannot ban other moderators
        if (isModerator && !isCreator && !isAdmin) {
            if (streamModeratorService.isModerator(creatorId, targetUserId)) {
                throw new SecurityException("Moderators cannot ban other moderators");
            }
            // Moderators cannot issue permanent bans
            if ("permanent".equals(banType)) {
                throw new SecurityException("Only the creator can issue permanent bans");
            }
        }
    }

    private String determineActorRole(Long creatorId, Long actorUserId, User actor) {
        if (actorUserId.equals(creatorId)) return "CREATOR";
        if (actor.getRole() == Role.ADMIN) return "ADMIN";
        if (streamModeratorService.isModerator(creatorId, actorUserId)) return "MODERATOR";
        return "UNKNOWN";
    }

    // ── Broadcasts ──────────────────────────────────────────────────

    private void broadcastBanMessage(Long creatorId, String username, String banType) {
        String durationText = switch (banType) {
            case "5m" -> "5 minutes";
            case "30m" -> "30 minutes";
            case "24h" -> "24 hours";
            case "permanent" -> "permanently";
            default -> banType;
        };
        String content = username + " was banned" + ("permanent".equals(banType) ? " permanently" : " for " + durationText);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BOT");
        payload.put("content", content);
        payload.put("senderUsername", "Livora AI");
        payload.put("senderRole", "BOT");
        payload.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/stream/" + creatorId + "/chat", payload);
    }

    private void broadcastUnbanMessage(Long creatorId, String username) {
        String content = username + " was unbanned";

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BOT");
        payload.put("content", content);
        payload.put("senderUsername", "Livora AI");
        payload.put("senderRole", "BOT");
        payload.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/stream/" + creatorId + "/chat", payload);
    }

    private void notifyBannedUser(Long userId, Long creatorId, String banType, Instant expiresAt) {
        RealtimeMessage event = RealtimeMessage.builder()
                .type("ROOM_BANNED")
                .payload(Map.of(
                        "creatorId", creatorId,
                        "banType", banType,
                        "expiresAt", expiresAt != null ? expiresAt.toString() : "never"
                ))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", event);
    }

    private void notifyUnbannedUser(Long userId, Long creatorId) {
        RealtimeMessage event = RealtimeMessage.builder()
                .type("ROOM_UNBANNED")
                .payload(Map.of("creatorId", creatorId))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", event);
    }

    private void broadcastBanListUpdate(Long creatorId) {
        RealtimeMessage event = RealtimeMessage.builder()
                .type("BAN_LIST_UPDATED")
                .payload(Map.of("creatorId", creatorId))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, event);
    }
}

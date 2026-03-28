package com.joinlivora.backend.moderation;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/room-bans")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('CREATOR', 'ADMIN', 'USER', 'PREMIUM')")
public class CreatorRoomBanController {

    private final CreatorRoomBanService banService;
    private final UserService userService;

    @PostMapping("/ban")
    public ResponseEntity<?> banUser(@RequestBody BanRequest request,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User actor = userService.getByEmail(userDetails.getUsername());
            banService.banUser(request.getCreatorId(), request.getTargetUserId(),
                    actor.getId(), request.getBanType(), request.getReason());
            return ResponseEntity.ok(Map.of("message", "User banned successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/unban")
    public ResponseEntity<?> unbanUser(@RequestBody UnbanRequest request,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User actor = userService.getByEmail(userDetails.getUsername());
            banService.unbanUser(request.getCreatorId(), request.getTargetUserId(), actor.getId());
            return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/active/{creatorId}")
    public ResponseEntity<?> getActiveBans(@PathVariable Long creatorId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        User actor = userService.getByEmail(userDetails.getUsername());
        if (!actor.getId().equals(creatorId) && actor.getRole() != com.joinlivora.backend.user.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }

        List<CreatorRoomBan> bans = banService.getActiveBans(creatorId);
        List<Map<String, Object>> response = bans.stream().map(ban -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", ban.getId().toString());
            map.put("targetUserId", ban.getTargetUser().getId());
            map.put("targetUsername", ban.getTargetUser().getUsername());
            map.put("issuedByUserId", ban.getIssuedBy().getId());
            map.put("issuedByUsername", ban.getIssuedBy().getUsername());
            map.put("banType", ban.getBanType());
            map.put("reason", ban.getReason());
            map.put("createdAt", ban.getCreatedAt().toString());
            map.put("expiresAt", ban.getExpiresAt() != null ? ban.getExpiresAt().toString() : null);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{creatorId}/{userId}")
    public ResponseEntity<?> checkBanStatus(@PathVariable Long creatorId, @PathVariable Long userId) {
        boolean banned = banService.isUserBanned(creatorId, userId);
        return ResponseEntity.ok(Map.of("banned", banned));
    }

    @GetMapping("/audit/{creatorId}")
    public ResponseEntity<?> getAuditHistory(@PathVariable Long creatorId,
                                              @AuthenticationPrincipal UserDetails userDetails,
                                              @RequestParam(defaultValue = "50") int limit) {
        User actor = userService.getByEmail(userDetails.getUsername());
        if (!actor.getId().equals(creatorId) && actor.getRole() != com.joinlivora.backend.user.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }

        List<ModerationAuditLog> logs = banService.getAuditHistory(creatorId, limit);
        List<Map<String, Object>> response = logs.stream().map(entry -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", entry.getId().toString());
            map.put("actionType", entry.getActionType());
            map.put("targetUserId", entry.getTargetUserId());
            map.put("targetUsername", entry.getTargetUsername());
            map.put("actorUserId", entry.getActorUserId());
            map.put("actorUsername", entry.getActorUsername());
            map.put("actorRole", entry.getActorRole());
            map.put("metadata", entry.getMetadata());
            map.put("createdAt", entry.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @Data
    public static class BanRequest {
        private Long creatorId;
        private Long targetUserId;
        private String banType; // "5m", "30m", "24h", "permanent"
        private String reason;
    }

    @Data
    public static class UnbanRequest {
        private Long creatorId;
        private Long targetUserId;
    }
}

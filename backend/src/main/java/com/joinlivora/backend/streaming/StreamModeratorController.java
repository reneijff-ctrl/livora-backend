package com.joinlivora.backend.streaming;

import com.joinlivora.backend.streaming.service.StreamModeratorService;
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
import java.util.Set;

/**
 * REST controller for per-creator-room moderator management.
 * Only the creator (or admin) can grant/revoke moderator privileges.
 */
@RestController
@RequestMapping("/api/stream/moderators")
@RequiredArgsConstructor
@Slf4j
public class StreamModeratorController {

    private final StreamModeratorService streamModeratorService;
    private final UserService userService;

    @PostMapping("/grant")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<?> grantModerator(
            @RequestBody ModeratorRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = resolveCreatorId(creator, request.getCreatorId());
        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator)) {
            return ResponseEntity.status(403).build();
        }

        // Cannot mod yourself
        if (creator.getId().equals(request.getUserId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot grant moderator to yourself"));
        }

        boolean persistent = request.getPersistent() != null ? request.getPersistent() : true;
        streamModeratorService.grantModerator(creatorId, request.getUserId(), persistent);
        streamModeratorService.broadcastModeratorChange(creatorId, creator.getUsername(), request.getUserId(), true);
        streamModeratorService.notifyModeratorStatusChange(request.getUserId(), creatorId, true);
        streamModeratorService.broadcastModeratorListUpdate(creatorId);

        return ResponseEntity.ok(Map.of("status", "granted", "persistent", persistent));
    }

    @PostMapping("/revoke")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<?> revokeModerator(
            @RequestBody ModeratorRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = resolveCreatorId(creator, request.getCreatorId());
        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator)) {
            return ResponseEntity.status(403).build();
        }

        streamModeratorService.revokeModerator(creatorId, request.getUserId());
        streamModeratorService.broadcastModeratorChange(creatorId, creator.getUsername(), request.getUserId(), false);
        streamModeratorService.notifyModeratorStatusChange(request.getUserId(), creatorId, false);
        streamModeratorService.broadcastModeratorListUpdate(creatorId);

        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    @PostMapping("/clear-all")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<?> clearAllModerators(
            @RequestBody ClearAllRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = resolveCreatorId(creator, request.getCreatorId());
        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator)) {
            return ResponseEntity.status(403).build();
        }

        // Notify all current mods before clearing
        Set<Long> currentMods = streamModeratorService.getModeratorIds(creatorId);
        streamModeratorService.clearAllModerators(creatorId);

        for (Long modId : currentMods) {
            streamModeratorService.notifyModeratorStatusChange(modId, creatorId, false);
        }

        streamModeratorService.broadcastAllModeratorsCleared(creatorId, creator.getUsername());
        streamModeratorService.broadcastModeratorListUpdate(creatorId);

        return ResponseEntity.ok(Map.of("status", "all_cleared", "count", currentMods.size()));
    }

    @GetMapping("/{creatorId}")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<Set<Long>> getModerators(@PathVariable Long creatorId) {
        return ResponseEntity.ok(streamModeratorService.getModeratorIds(creatorId));
    }

    /**
     * Viewer-accessible endpoint to check if the current user is a moderator for a given creator.
     * Any authenticated user can check their own moderator status.
     */
    @GetMapping("/check/{creatorId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> checkOwnModeratorStatus(
            @PathVariable Long creatorId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        boolean isMod = streamModeratorService.isModerator(creatorId, user.getId());
        return ResponseEntity.ok(Map.of("isModerator", isMod, "userId", user.getId(), "creatorId", creatorId));
    }

    @GetMapping("/{creatorId}/details")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getModeratorDetails(@PathVariable Long creatorId) {
        return ResponseEntity.ok(streamModeratorService.getModeratorDetails(creatorId));
    }

    private Long resolveCreatorId(User creator, Long requestCreatorId) {
        if (requestCreatorId != null) return requestCreatorId;
        if (creator.getRole() == com.joinlivora.backend.user.Role.CREATOR) return creator.getId();
        return null;
    }

    private boolean isAdmin(User user) {
        return user.getRole() == com.joinlivora.backend.user.Role.ADMIN;
    }

    @Data
    public static class ModeratorRequest {
        private Long creatorId;
        private Long userId;
        private Boolean persistent;
    }

    @Data
    public static class ClearAllRequest {
        private Long creatorId;
    }
}

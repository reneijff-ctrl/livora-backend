package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.dto.UserResponse;
import com.joinlivora.backend.websocket.PresenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stream/moderation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('CREATOR', 'ADMIN', 'USER', 'PREMIUM')")
public class StreamModerationController {

    private final StreamModerationService streamModerationService;
    private final com.joinlivora.backend.streaming.service.StreamModeratorService streamModeratorService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final PresenceService presenceService;
    private final UserService userService;
    private final ModerationSettingsRepository settingsRepository;
    private final ChatModerationService chatModerationService;
    private final StreamRepository streamRepository;
    private final com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;
    private final CreatorFollowRepository creatorFollowRepository;
    private final com.joinlivora.backend.moderation.CreatorRoomBanService creatorRoomBanService;
    private final com.joinlivora.backend.moderation.ModerationAuditLogRepository moderationAuditLogRepository;

    @GetMapping("/viewers/{creatorUserId}")
    public ResponseEntity<List<UserResponse>> getViewers(
            @PathVariable Long creatorUserId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        if (!creator.getId().equals(creatorUserId) && !isAdmin(creator) && !streamModeratorService.isModerator(creatorUserId, creator.getId())) {
            return ResponseEntity.status(403).build();
        }

        List<UserResponse> viewers = presenceService.getCreatorViewerList(creatorUserId);

        if (!viewers.isEmpty()) {
            List<Long> viewerIds = viewers.stream().map(UserResponse::getId).collect(Collectors.toList());
            Set<Long> followerIds = creatorFollowRepository.findFollowerIdsByCreatorIdAndFollowerIds(creatorUserId, viewerIds);
            Set<Long> moderatorIds = streamModeratorService.getModeratorIds(creatorUserId);
            // Filter out room-banned users from viewer list
            viewers.removeIf(v -> creatorRoomBanService.isUserBanned(creatorUserId, v.getId()));
            viewers.forEach(v -> {
                v.setIsFollower(followerIds.contains(v.getId()));
                v.setIsModerator(moderatorIds.contains(v.getId()));
            });
        }

        return ResponseEntity.ok(viewers);
    }

    @PostMapping("/mute")
    public ResponseEntity<Void> muteUser(
            @RequestBody StreamMuteRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = request.getCreatorId();
        if (creatorId == null && request.getStreamId() != null) {
            creatorId = streamRepository.findById(request.getStreamId())
                    .map(s -> s.getCreator().getId())
                    .orElse(null);
        }

        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator) && !streamModeratorService.isModerator(creatorId, creator.getId())) {
            return ResponseEntity.status(403).build();
        }

        // Moderators cannot moderate the creator or other moderators
        if (streamModeratorService.isModerator(creatorId, creator.getId())) {
            if (request.getUserId().equals(creatorId)) return ResponseEntity.status(403).build();
            if (streamModeratorService.isModerator(creatorId, request.getUserId())) return ResponseEntity.status(403).build();
        }

        int duration = request.getDurationMinutes() > 0 ? request.getDurationMinutes() : 60;
        streamModerationService.muteUser(creatorId, request.getUserId(), duration);

        try {
            User mutedUser = userService.getById(request.getUserId());
            adminRealtimeEventService.broadcastUserMuted(creator.getUsername(), mutedUser.getUsername(), duration);
            String actorRole = creator.getId().equals(creatorId) ? "CREATOR" : (isAdmin(creator) ? "ADMIN" : "MODERATOR");
            creatorRoomBanService.logAudit("MUTE", creatorId, request.getUserId(), mutedUser.getUsername(),
                    creator.getId(), creator.getUsername(), actorRole, "{\"durationMinutes\":" + duration + "}");
        } catch (Exception e) {
            log.warn("Failed to broadcast user mute event: {}", e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/shadow-mute")
    public ResponseEntity<Void> shadowMuteUser(
            @RequestBody StreamModerationRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = request.getCreatorId();
        if (creatorId == null && request.getStreamId() != null) {
            creatorId = streamRepository.findById(request.getStreamId())
                    .map(s -> s.getCreator().getId())
                    .orElse(null);
        }

        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator) && !streamModeratorService.isModerator(creatorId, creator.getId())) {
            return ResponseEntity.status(403).build();
        }

        // Moderators cannot moderate the creator or other moderators
        if (streamModeratorService.isModerator(creatorId, creator.getId())) {
            if (request.getUserId().equals(creatorId)) return ResponseEntity.status(403).build();
            if (streamModeratorService.isModerator(creatorId, request.getUserId())) return ResponseEntity.status(403).build();
        }

        streamModerationService.shadowMuteUser(creatorId, request.getUserId());
        try {
            User mutedUser = userService.getById(request.getUserId());
            adminRealtimeEventService.broadcastUserShadowMuted(creator.getUsername(), mutedUser.getUsername());
            String actorRole = creator.getId().equals(creatorId) ? "CREATOR" : (isAdmin(creator) ? "ADMIN" : "MODERATOR");
            creatorRoomBanService.logAudit("SHADOW_MUTE", creatorId, request.getUserId(), mutedUser.getUsername(),
                    creator.getId(), creator.getUsername(), actorRole, null);
        } catch (Exception e) {
            log.warn("Failed to broadcast user shadow mute event: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/kick")
    public ResponseEntity<Void> kickUser(
            @RequestBody StreamModerationRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        Long creatorId = request.getCreatorId();
        if (creatorId == null && request.getStreamId() != null) {
            creatorId = streamRepository.findById(request.getStreamId())
                    .map(s -> s.getCreator().getId())
                    .orElse(null);
        }

        if (creatorId == null) return ResponseEntity.badRequest().build();

        if (!creator.getId().equals(creatorId) && !isAdmin(creator) && !streamModeratorService.isModerator(creatorId, creator.getId())) {
            return ResponseEntity.status(403).build();
        }

        // Moderators cannot moderate the creator or other moderators
        if (streamModeratorService.isModerator(creatorId, creator.getId())) {
            if (request.getUserId().equals(creatorId)) return ResponseEntity.status(403).build();
            if (streamModeratorService.isModerator(creatorId, request.getUserId())) return ResponseEntity.status(403).build();
        }

        streamModerationService.kickUser(creatorId, request.getUserId());
        try {
            User kickedUser = userService.getById(request.getUserId());
            String actorRole = creator.getId().equals(creatorId) ? "CREATOR" : (isAdmin(creator) ? "ADMIN" : "MODERATOR");
            creatorRoomBanService.logAudit("KICK", creatorId, request.getUserId(), kickedUser.getUsername(),
                    creator.getId(), creator.getUsername(), actorRole, null);
        } catch (Exception e) {
            log.warn("Failed to log kick audit: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings/{creatorUserId}")
    public ResponseEntity<com.joinlivora.backend.streaming.dto.ModerationSettingsResponse> getSettings(@PathVariable Long creatorUserId) {

        java.util.Optional<ModerationSettings> settingsOpt =
                settingsRepository.findByCreatorUserId(creatorUserId);

        ModerationSettings settings = settingsOpt.orElseGet(() -> {
            ModerationSettings newSettings = new ModerationSettings();
            newSettings.setCreatorUserId(creatorUserId);
            newSettings.setBannedWords("");
            newSettings.setStrictMode(false);
            return settingsRepository.save(newSettings);
        });

        return ResponseEntity.ok(new com.joinlivora.backend.streaming.dto.ModerationSettingsResponse(settings));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody com.joinlivora.backend.streaming.dto.ModerationSettingsRequest request) {

        ModerationSettings settings = settingsRepository
                .findByCreatorUserId(request.getCreatorUserId())
                .orElseGet(() -> {
                    ModerationSettings s = new ModerationSettings();
                    s.setCreatorUserId(request.getCreatorUserId());
                    return s;
                });

        settings.setAutoPinLargeTips(request.isAutoPinLargeTips());
        settings.setAiHighlightEnabled(request.isAiHighlightEnabled());
        settings.setStrictMode(request.isStrictMode());

        settings.setBannedWords(
                request.getBannedWords() == null
                        ? ""
                        : String.join(",", request.getBannedWords())
        );

        settingsRepository.save(settings);
        chatModerationService.invalidateCreatorCache(request.getCreatorUserId());

        return ResponseEntity.ok().build();
    }

    private boolean isAdmin(User user) {
        return user.getRole() == com.joinlivora.backend.user.Role.ADMIN;
    }

    @Data
    public static class StreamModerationRequest {
        private Long creatorId;
        private java.util.UUID streamId;
        private Long userId;
    }

    @Data
    public static class StreamMuteRequest {
        private Long creatorId;
        private java.util.UUID streamId;
        private Long userId;
        private int durationMinutes;
    }
}

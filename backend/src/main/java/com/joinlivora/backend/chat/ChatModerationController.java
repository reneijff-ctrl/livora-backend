package com.joinlivora.backend.chat;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CREATOR', 'ADMIN', 'USER')")
public class ChatModerationController {

    private final ChatModerationService moderationService;
    private final SlowModeBypassService slowModeBypassService;
    private final PPVChatAccessService ppvChatAccessService;
    private final UserService userService;
    private final AuditService auditService;
    private final StreamRepository streamRepository;
    private final StreamModeratorService streamModeratorService;

    @PostMapping("/mute")
    public ResponseEntity<Void> muteUser(
            @RequestBody MuteRequest request,
            @AuthenticationPrincipal UserDetails moderatorDetails
    ) {
        User moderator = userService.getByEmail(moderatorDetails.getUsername());
        validateModeratorPermission(moderator, request.getRoomId());
        
        moderationService.muteUser(
                request.getUserId(), 
                moderator.getId(), 
                java.time.Duration.ofSeconds(request.getDurationSeconds()), 
                request.getRoomId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shadow-mute")
    public ResponseEntity<Void> shadowMuteUser(
            @RequestBody MuteRequest request,
            @AuthenticationPrincipal UserDetails moderatorDetails
    ) {
        User moderator = userService.getByEmail(moderatorDetails.getUsername());
        validateModeratorPermission(moderator, request.getRoomId());

        moderationService.shadowMuteUser(
                request.getUserId(),
                moderator.getId(),
                java.time.Duration.ofSeconds(request.getDurationSeconds()),
                request.getRoomId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ban")
    public ResponseEntity<Void> banUser(
            @RequestBody BanRequest request,
            @AuthenticationPrincipal UserDetails moderatorDetails,
            HttpServletRequest httpRequest
    ) {
        User moderator = userService.getByEmail(moderatorDetails.getUsername());
        validateModeratorPermission(moderator, request.getRoomId());

        moderationService.banUser(request.getUserId(), moderator.getId(), request.getRoomId());
        
        auditService.logEvent(
                new UUID(0L, moderator.getId()),
                AuditService.ACCOUNT_SUSPENDED,
                "USER",
                new UUID(0L, request.getUserId()),
                Map.of("action", "chat_ban", "type", "Moderator action", "roomId", request.getRoomId()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> deleteMessage(
            @RequestBody DeleteRequest request,
            @AuthenticationPrincipal UserDetails moderatorDetails,
            HttpServletRequest httpRequest
    ) {
        User moderator = userService.getByEmail(moderatorDetails.getUsername());
        validateModeratorPermission(moderator, request.getRoomId());

        moderationService.deleteMessage(request.getRoomId(), request.getMessageId(), moderator.getId());
        
        auditService.logEvent(
                new UUID(0L, moderator.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "CHAT_MESSAGE",
                null,
                Map.of("messageId", request.getMessageId(), "roomId", request.getRoomId()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    private void validateModeratorPermission(User moderator, String roomId) {
        if (moderator.getRole() == Role.ADMIN) {
            return;
        }
        
        if (roomId != null && roomId.startsWith("stream-")) {
            try {
                UUID streamId = UUID.fromString(roomId.substring(7));
                com.joinlivora.backend.streaming.Stream room = streamRepository.findByIdWithCreator(streamId)
                        .orElseGet(() -> streamRepository.findByMediasoupRoomIdWithCreator(streamId)
                                .orElseThrow(() -> new RuntimeException("Stream room not found")));
                Long creatorId = room.getCreator().getId();
                // Allow if user is the creator
                if (creatorId.equals(moderator.getId())) {
                    return;
                }
                // Allow if user is a per-stream moderator for this creator
                if (streamModeratorService.isModerator(creatorId, moderator.getId())) {
                    return;
                }
                throw new AccessDeniedException("You do not have moderation permissions for this stream");
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid stream ID format in roomId");
            }
        } else if (moderator.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Creators can only moderate their own streams");
        }
    }

    @PostMapping("/revoke-bypass")
    public ResponseEntity<Void> revokeBypass(@RequestBody RevokeBypassRequest request) {
        slowModeBypassService.revokeBypass(request.getUserId(), request.getRoomId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/grant-ppv-access")
    public ResponseEntity<Void> grantPpvAccess(@RequestBody GrantPpvAccessRequest request) {
        ppvChatAccessService.grantAccess(request.getUserId(), request.getRoomId(), request.getExpiresAt());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke-ppv-access")
    public ResponseEntity<Void> revokePpvAccess(@RequestBody RevokePpvAccessRequest request) {
        ppvChatAccessService.revokeAccess(request.getUserId(), request.getRoomId());
        return ResponseEntity.ok().build();
    }
}

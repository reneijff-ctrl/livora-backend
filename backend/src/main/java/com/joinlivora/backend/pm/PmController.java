package com.joinlivora.backend.pm;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class PmController {

    private final PmService pmService;
    private final UserService userService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PmSessionDto> startSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PmStartRequest request
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        PmSessionDto session = pmService.startSession(creator.getId(), request.getViewerId());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/active")
    public ResponseEntity<List<PmSessionDto>> getActiveSessions(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        List<PmSessionDto> sessions = pmService.getActiveSessions(user.getId());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        List<ChatMessageDto> messages = pmService.getMessages(roomId, user.getId());
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        pmService.markAsRead(roomId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/end")
    public ResponseEntity<Void> endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        pmService.endSession(roomId, user.getId());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class PmStartRequest {
        private Long viewerId;
    }
}

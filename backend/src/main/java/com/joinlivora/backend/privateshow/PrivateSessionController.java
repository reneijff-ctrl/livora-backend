package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/private-show")
@RequiredArgsConstructor
public class PrivateSessionController {

    private final PrivateSessionService sessionService;
    private final UserService userService;

    @PostMapping("/request")
    public ResponseEntity<PrivateSessionDto> requestPrivateShow(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PrivateSessionRequestDto request
    ) {
        User viewer = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.requestPrivateShow(viewer, request.getUserId(), request.getPricePerMinute());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/active")
    public ResponseEntity<PrivateSessionDto> getActiveSession(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.getActiveSessionForUser(user.getId());
        if (session == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<PrivateSessionDto> getSession(@PathVariable UUID sessionId) {
        PrivateSessionDto session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/accept")
    public ResponseEntity<PrivateSessionDto> acceptRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.acceptRequest(creator, sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/reject")
    public ResponseEntity<PrivateSessionDto> rejectRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.rejectRequest(creator, sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<PrivateSessionDto> startSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.startSession(creator, sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<PrivateSessionDto> endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        PrivateSessionDto session = sessionService.endSession(user, sessionId, "Ended by user");
        return ResponseEntity.ok(session);
    }

    // ===================== SPY ENDPOINTS =====================

    @PostMapping("/{sessionId}/spy")
    public ResponseEntity<PrivateSpySessionDto> joinAsSpy(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User viewer = userService.getByEmail(userDetails.getUsername());
        PrivateSpySessionDto spy = sessionService.joinAsSpy(viewer, sessionId);
        return ResponseEntity.ok(spy);
    }

    @PostMapping("/spy/{spySessionId}/leave")
    public ResponseEntity<Void> leaveSpySession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID spySessionId
    ) {
        User viewer = userService.getByEmail(userDetails.getUsername());
        sessionService.leaveSpySession(viewer, spySessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/spy/count")
    public ResponseEntity<Integer> getSpyCount(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.getActiveSpyCount(sessionId));
    }

    @GetMapping("/{sessionId}/spy/active")
    public ResponseEntity<PrivateSpySessionDto> getActiveSpySession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId
    ) {
        User viewer = userService.getByEmail(userDetails.getUsername());
        PrivateSpySessionDto spy = sessionService.getActiveSpySession(viewer, sessionId);
        if (spy == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(spy);
    }

    @GetMapping("/active-for-creator/{creatorUserId}")
    public ResponseEntity<PrivateSessionDto> getActiveSessionForCreator(@PathVariable Long creatorUserId) {
        PrivateSessionDto session = sessionService.getActiveSessionForCreator(creatorUserId);
        if (session == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(session);
    }

    @GetMapping("/creator/{creatorUserId}/availability")
    public ResponseEntity<PrivateSessionAvailabilityDto> getAvailability(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long creatorUserId
    ) {
        Long currentUserId = null;
        if (userDetails != null) {
            currentUserId = userService.getByEmail(userDetails.getUsername()).getId();
        }
        PrivateSessionAvailabilityDto availability = sessionService.getAvailability(creatorUserId, currentUserId);
        return ResponseEntity.ok(availability);
    }

    @Data
    public static class PrivateSessionRequestDto {
        private Long userId;
        private long pricePerMinute;
    }
}

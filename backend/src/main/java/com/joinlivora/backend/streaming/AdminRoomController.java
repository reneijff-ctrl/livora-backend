package com.joinlivora.backend.streaming;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoomController {

    private final StreamService streamService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/active")
    public ResponseEntity<List<StreamRoom>> getActiveRooms() {
        return ResponseEntity.ok(streamService.getActiveRooms());
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> closeRoom(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        streamService.closeRoom(id);
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "STREAM_ROOM",
                id,
                Map.of("action", "close"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/enable-slow-mode")
    public ResponseEntity<Void> enableSlowMode(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        streamService.setSlowMode(id, true);
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.ROOM_MODERATION,
                "STREAM_ROOM",
                id,
                Map.of("action", "enable_slow_mode"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/disable-slow-mode")
    public ResponseEntity<Void> disableSlowMode(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        streamService.setSlowMode(id, false);
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.ROOM_MODERATION,
                "STREAM_ROOM",
                id,
                Map.of("action", "disable_slow_mode"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }
}

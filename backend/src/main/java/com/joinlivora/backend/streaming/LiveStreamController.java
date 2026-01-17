package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@Slf4j
public class LiveStreamController {

    private final StreamService streamService;
    private final UserService userService;

    @GetMapping("/live")
    public ResponseEntity<List<StreamRoom>> getLiveStreams() {
        return ResponseEntity.ok(streamService.getLiveStreams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamRoom> getStream(@PathVariable UUID id) {
        return ResponseEntity.ok(streamService.getRoom(id));
    }

    @PostMapping("/creator/start")
    @PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
    public ResponseEntity<StreamRoom> startStream(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        String title = (String) payload.getOrDefault("title", "Live Stream");
        String description = (String) payload.getOrDefault("description", "");
        Long minChatTokens = payload.get("minChatTokens") != null ? Long.valueOf(payload.get("minChatTokens").toString()) : null;
        
        return ResponseEntity.ok(streamService.startStream(creator, title, description, minChatTokens));
    }

    @PostMapping("/creator/stop")
    @PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
    public ResponseEntity<StreamRoom> stopStream(@AuthenticationPrincipal UserDetails userDetails) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(streamService.stopStream(creator));
    }

    @GetMapping("/creator/room")
    @PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
    public ResponseEntity<StreamRoom> getMyRoom(@AuthenticationPrincipal UserDetails userDetails) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(streamService.getCreatorRoom(creator));
    }
}

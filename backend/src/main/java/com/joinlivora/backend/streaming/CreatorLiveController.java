package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/creator/live")
@RequiredArgsConstructor
@Slf4j
public class CreatorLiveController {

    private final StreamService streamService;
    private final UserService userService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getLiveStatus(Principal principal) {
        User creator = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        StreamRoom room = streamService.getCreatorRoom(creator);
        
        return ResponseEntity.ok(Map.of(
                "isLive", room.isLive(),
                "viewerCount", room.getViewerCount(),
                "streamTitle", room.getStreamTitle() != null ? room.getStreamTitle() : ""
        ));
    }
}

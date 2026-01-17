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
@RequestMapping("/api/admin/streams")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminStreamController {

    private final StreamService streamService;
    private final UserService userService;
    private final BadgeService badgeService;
    private final BadgeRepository badgeRepository;

    @GetMapping
    public ResponseEntity<List<StreamRoom>> getAllStreams() {
        return ResponseEntity.ok(streamService.getLiveStreams());
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> forceStopStream(@PathVariable UUID id) {
        StreamRoom room = streamService.getRoom(id);
        streamService.stopStream(room.getCreator());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badges")
    public ResponseEntity<Badge> createBadge(@RequestBody Map<String, Object> payload) {
        Badge badge = Badge.builder()
                .name((String) payload.get("name"))
                .tokenCost(Long.valueOf(payload.get("tokenCost").toString()))
                .durationDays(payload.get("durationDays") != null ? Integer.valueOf(payload.get("durationDays").toString()) : null)
                .build();
        return ResponseEntity.ok(badgeRepository.save(badge));
    }
}

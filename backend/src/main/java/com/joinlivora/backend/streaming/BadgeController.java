package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
@Slf4j
public class BadgeController {

    private final BadgeService badgeService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Badge>> getBadges() {
        return ResponseEntity.ok(badgeService.getAllBadges());
    }

    @PostMapping("/purchase/{badgeId}")
    public ResponseEntity<UserBadge> purchaseBadge(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID badgeId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(badgeService.purchaseBadge(user, badgeId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<UserBadge>> getMyBadges(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(badgeService.getUserBadges(user));
    }
}

package com.joinlivora.backend.creator.follow.controller;

import com.joinlivora.backend.creator.follow.dto.FollowStatusResponse;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creators/{creatorId}/follow")
@RequiredArgsConstructor
@Slf4j
public class CreatorFollowController {

    private final CreatorFollowService creatorFollowService;
    private final UserService userService;
    private final StreamAssistantBotService streamAssistantBotService;
    private final LiveViewerCounterService liveViewerCounterService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowStatusResponse> follow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorId
    ) {
        User follower = userService.getById(principal.getUserId());
        User creator = userService.getById(creatorId);
        boolean isNewFollow = creatorFollowService.follow(follower, creator);

        if (isNewFollow) {
            try {
                Long activeSession = liveViewerCounterService.getActiveSessionId(creatorId);
                if (activeSession != null) {
                    String displayName = follower.getDisplayName() != null ? follower.getDisplayName() : follower.getUsername();
                    streamAssistantBotService.onNewFollow(creatorId, displayName);
                }
            } catch (Exception e) {
                log.warn("Failed to send follow bot message for creator {}: {}", creatorId, e.getMessage());
            }
        }

        return ResponseEntity.ok(getStatusResponse(follower, creator));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowStatusResponse> unfollow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorId
    ) {
        User follower = userService.getById(principal.getUserId());
        User creator = userService.getById(creatorId);
        creatorFollowService.unfollow(follower, creator);
        return ResponseEntity.ok(getStatusResponse(follower, creator));
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowStatusResponse> getStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorId
    ) {
        User follower = userService.getById(principal.getUserId());
        User creator = userService.getById(creatorId);
        return ResponseEntity.ok(getStatusResponse(follower, creator));
    }

    private FollowStatusResponse getStatusResponse(User follower, User creator) {
        return FollowStatusResponse.builder()
                .following(creatorFollowService.isFollowing(follower, creator))
                .followers(creatorFollowService.getFollowerCount(creator))
                .build();
    }
}

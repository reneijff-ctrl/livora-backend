package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorPostResponse;
import com.joinlivora.backend.creator.service.CreatorPostService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final CreatorPostService creatorPostService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<CreatorPostResponse>> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User user = userService.getById(principal.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        Page<CreatorPostResponse> feed = creatorPostService.getFeed(user, pageable);
        return ResponseEntity.ok(feed);
    }
}

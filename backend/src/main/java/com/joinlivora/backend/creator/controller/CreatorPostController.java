package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreateCreatorPostRequest;
import com.joinlivora.backend.creator.dto.CreatorPostResponse;
import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.service.CreatorPostService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class CreatorPostController {

    private final CreatorPostService creatorPostService;
    private final UserService userService;

    @PostMapping("/api/creator/posts")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorPostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCreatorPostRequest request
    ) {
        User creator = userService.getById(principal.getUserId());
        CreatorPost post = creatorPostService.createPost(creator, request.getTitle(), request.getContent());
        return ResponseEntity.ok(creatorPostService.mapToResponse(post));
    }

    @GetMapping("/api/creators/{identifier}/posts")
    public ResponseEntity<List<CreatorPostResponse>> getPosts(
            @PathVariable String identifier,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        User currentUser = principal != null ? userService.getById(principal.getUserId()) : null;
        return ResponseEntity.ok(creatorPostService.getPostsForCreator(identifier, currentUser));
    }

    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<CreatorPostResponse> getPost(
            @PathVariable java.util.UUID postId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        User currentUser = principal != null ? userService.getById(principal.getUserId()) : null;
        return ResponseEntity.ok(creatorPostService.getPost(postId, currentUser));
    }

    @PostMapping("/api/posts/{postId}/like")
    public ResponseEntity<Void> likePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable java.util.UUID postId
    ) {
        User user = userService.getById(principal.getUserId());
        creatorPostService.likePost(user, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/posts/{postId}/like")
    public ResponseEntity<Void> unlikePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable java.util.UUID postId
    ) {
        User user = userService.getById(principal.getUserId());
        creatorPostService.unlikePost(user, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/posts/{postId}/likes/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable java.util.UUID postId) {
        return ResponseEntity.ok(creatorPostService.getLikeCount(postId));
    }

    @GetMapping("/api/posts/public")
    public ResponseEntity<org.springframework.data.domain.Page<CreatorPostResponse>> getPublicPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            page, size, org.springframework.data.domain.Sort.by("createdAt").descending()
        );
        User currentUser = principal != null ? userService.getById(principal.getUserId()) : null;
        return ResponseEntity.ok(creatorPostService.getPublicPosts(pageable, currentUser));
    }

}

package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.content.Content;
import com.joinlivora.backend.content.ContentService;
import com.joinlivora.backend.content.MediaUnlockService;
import com.joinlivora.backend.content.dto.ContentResponse;
import com.joinlivora.backend.content.dto.UpdateContentRequest;
import com.joinlivora.backend.util.UrlUtils;
import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.service.CreatorPostService;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.CreatorSearchService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.dto.PublicViewerResponse;
import com.joinlivora.backend.websocket.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorProfileService creatorProfileService;
    private final CreatorSearchService creatorSearchService;
    private final CreatorPostService creatorPostService;
    private final CreatorFollowService followService;
    private final UserService userService;
    private final CreatorPresenceService creatorPresenceService;
    private final ContentService contentService;
    private final MediaUnlockService mediaUnlockService;
    private final PresenceService presenceService;

    @GetMapping("/online")
    public ResponseEntity<List<HomepageCreatorDto>> getOnlineCreators() {
        return ResponseEntity.ok(creatorSearchService.getPublicCreatorsForHomepage());
    }

    @GetMapping("/posts/explore")
    public ResponseEntity<Page<ExplorePostResponse>> getExplorePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(creatorPostService.getExplorePosts(pageable));
    }

    @GetMapping("/explore")
    public ResponseEntity<List<ExploreCreatorResponse>> getExploreCreators() {
        List<ExploreCreatorResponse> creators = creatorSearchService.getExploreCreators();
        if (creators.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(creators);
    }

    @GetMapping("/public")
    public ResponseEntity<Page<ExploreCreatorDto>> getPublicCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorSearchService.getExploreCreatorsList(pageable));
    }

    @GetMapping
    public ResponseEntity<Page<CreatorProfileDTO>> getCreators(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean liveOnly,
            @RequestParam(required = false, defaultValue = "all") String paidFilter,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false) String bodyType,
            @RequestParam(required = false) String hairColor,
            @RequestParam(required = false) String eyeColor,
            @RequestParam(required = false) String ethnicity,
            @RequestParam(required = false) String interestedIn,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorSearchService.getCreators(category, search, country, liveOnly, paidFilter, sort, bodyType, hairColor, eyeColor, ethnicity, interestedIn, language, pageable));
    }


    @GetMapping("/profile/{identifier}")
    public ResponseEntity<PublicCreatorProfileResponse> getPublicCreatorProfile(@PathVariable String identifier) {
        return creatorProfileService.getPublicProfile(identifier)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Unified endpoint: resolves by profile ID, user ID, or username
    @GetMapping("/{username}")
    public ResponseEntity<PublicCreatorInfoResponse> getPublicCreatorByUsername(@PathVariable String username) {
        return creatorProfileService.getPublicCreatorInfo(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<CreatorProfileByUsernameResponse> getProfileByUsername(@PathVariable String username) {
        return creatorProfileService.getProfileByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{username}/followers/count")
    public ResponseEntity<Long> getFollowerCount(@PathVariable String username) {
        long followers = creatorProfileService.getFollowerCountStrict(username);
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/me/stats")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorStatsResponse> getMyStats(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        return ResponseEntity.ok(creatorProfileService.getCreatorStats(creator));
    }

    @GetMapping("/me/earnings")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorEarningsDashboardResponse> getMyEarnings(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        return ResponseEntity.ok(creatorProfileService.getCreatorEarningsDashboard(creator));
    }


    @GetMapping("/{id}/media")
    public ResponseEntity<List<ContentResponse>> getCreatorMedia(@PathVariable Long id) {
        User creator = userService.getById(id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = null;

        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails) {
            User user = userService.findByEmail(authentication.getName());
            currentUserId = user.getId();
        }

        final Long finalCurrentUserId = currentUserId;

        List<ContentResponse> media = contentService.getCreatorContent(creator).stream()
                .map(content -> {
                    ContentResponse contentResponse = mapToContentResponse(content);
                    boolean unlocked = false;
                    if (finalCurrentUserId != null) {
                        unlocked = mediaUnlockService.isUnlockedByUser(finalCurrentUserId, content.getId());
                    }
                    contentResponse.setUnlocked(unlocked);
                    return contentResponse;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(media);
    }

    @GetMapping("/{creatorUserId}/viewers")
    public ResponseEntity<List<PublicViewerResponse>> getViewers(@PathVariable Long creatorUserId) {
        List<PublicViewerResponse> viewers = presenceService.getPublicViewerList(creatorUserId);
        return ResponseEntity.ok(viewers);
    }

    private ContentResponse mapToContentResponse(Content content) {
        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(UrlUtils.sanitizeUrl(content.getThumbnailUrl()))
                .mediaUrl(UrlUtils.sanitizeUrl(content.getMediaUrl()))
                .accessLevel(content.getAccessLevel())
                .type(content.getType())
                .creatorId(content.getCreator().getId())
                .unlockPriceTokens(content.getUnlockPriceTokens())
                .createdAt(content.getCreatedAt())
                .build();
    }

}

package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.ContentResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/content", "/api/auth/content"})
@RequiredArgsConstructor
@Slf4j
public class ContentController {

    private final ContentService contentService;
    private final UserService userService;
    private final MediaAccessService mediaAccessService;
    private final com.joinlivora.backend.payment.SubscriptionService subscriptionService;

    @GetMapping("/public")
    @Cacheable("publicContent")
    public List<ContentResponse> getPublicContent() {
        return contentService.getPublicContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/feed")
    public List<ContentResponse> getFeed(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        boolean hasPremium = subscriptionService.getSubscriptionForUser(user).getStatus().name().equals("ACTIVE");
        
        return contentService.getFeedContent(user, hasPremium).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/premium")
    public List<ContentResponse> getPremiumContent(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        boolean hasPremium = subscriptionService.getSubscriptionForUser(user).getStatus().name().equals("ACTIVE");
        
        if (!hasPremium && !user.getRole().name().equals("ADMIN")) {
            return List.of(); // Or throw 403, but typically feed filtering handles this
        }
        
        return contentService.getPremiumContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContentResponse> getContent(@PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        Content content = contentService.getContentById(id);
        User user = userService.getByEmail(userDetails.getUsername());
        
        if (!hasAccess(user, content)) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(mapToResponse(content));
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<String> getMediaAccess(@PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        Content content = contentService.getContentById(id);
        User user = userService.getByEmail(userDetails.getUsername());
        
        if (!hasAccess(user, content)) {
            return ResponseEntity.status(403).build();
        }
        
        String signedUrl = mediaAccessService.generateSignedUrl(content);
        return ResponseEntity.ok(signedUrl);
    }

    private boolean hasAccess(User user, Content content) {
        if (content.getAccessLevel() == ContentAccessLevel.FREE) return true;
        
        if (content.getAccessLevel() == ContentAccessLevel.PREMIUM) {
            return subscriptionService.getSubscriptionForUser(user).getStatus().name().equals("ACTIVE") || 
                   user.getRole().name().equals("ADMIN");
        }
        
        if (content.getAccessLevel() == ContentAccessLevel.CREATOR) {
            return user.getRole().name().equals("CREATOR") || user.getRole().name().equals("ADMIN");
        }
        
        return false;
    }

    private ContentResponse mapToResponse(Content content) {
        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(content.getThumbnailUrl())
                // Do not expose mediaUrl directly in general list responses if possible, 
                // or ensure it's not the raw storage URL
                .mediaUrl(null) 
                .accessLevel(content.getAccessLevel())
                .creatorId(content.getCreator().getId())
                .creatorEmail(content.getCreator().getEmail())
                .createdAt(content.getCreatedAt())
                .build();
    }
}

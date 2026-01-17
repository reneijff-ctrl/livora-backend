package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.ContentResponse;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
@Slf4j
public class CreatorContentController {

    private final ContentService contentService;
    private final UserService userService;

    @GetMapping("/content/mine")
    public List<ContentResponse> getMyContent(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return contentService.getCreatorContent(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/content")
    public ContentResponse createContent(@RequestBody Content contentRequest, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        contentRequest.setCreator(user);
        Content content = contentService.createContent(contentRequest);
        return mapToResponse(content);
    }

    @PutMapping("/content/{id}")
    public ContentResponse updateContent(@PathVariable UUID id, @RequestBody Content contentRequest, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        Content content = contentService.updateContent(id, contentRequest, user);
        return mapToResponse(content);
    }

    @DeleteMapping("/content/{id}")
    public ResponseEntity<Void> deleteContent(@PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        contentService.deleteContent(id, user);
        return ResponseEntity.ok().build();
    }

    private ContentResponse mapToResponse(Content content) {
        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(content.getThumbnailUrl())
                .mediaUrl(content.getMediaUrl()) // Creator can see their own media URL or we sign it
                .accessLevel(content.getAccessLevel())
                .creatorId(content.getCreator().getId())
                .creatorEmail(content.getCreator().getEmail())
                .createdAt(content.getCreatedAt())
                .build();
    }
}

package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.ContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final ContentService contentService;

    @GetMapping
    public List<ContentResponse> getAllContent() {
        return contentService.getAllContentForAdmin().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<Void> disableContent(@PathVariable UUID id) {
        contentService.disableContent(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContent(@PathVariable UUID id) {
        // Admin can delete anything, passing null or a dummy admin user if needed
        // but let's assume Service handles it or we use a more explicit method
        contentService.deleteContent(id, null); // Need to handle null in Service or use Admin check
        return ResponseEntity.ok().build();
    }

    private ContentResponse mapToResponse(Content content) {
        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(content.getThumbnailUrl())
                .mediaUrl(content.getMediaUrl())
                .accessLevel(content.getAccessLevel())
                .creatorId(content.getCreator().getId())
                .creatorEmail(content.getCreator().getEmail())
                .createdAt(content.getCreatedAt())
                .build();
    }
}

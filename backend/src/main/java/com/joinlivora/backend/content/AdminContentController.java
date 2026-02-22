package com.joinlivora.backend.content;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.content.dto.ContentResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final ContentService contentService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping
    public List<ContentResponse> getAllContent() {
        return contentService.getAllContentForAdmin().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<Void> disableContent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        contentService.disableContent(id);
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "CONTENT",
                id,
                Map.of("action", "disable"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        contentService.deleteContent(id, admin);
        
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "CONTENT",
                id,
                Map.of("action", "delete"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
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

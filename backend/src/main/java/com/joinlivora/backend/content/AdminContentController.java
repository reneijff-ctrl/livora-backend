package com.joinlivora.backend.content;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.content.dto.ContentAdminDTO;
import com.joinlivora.backend.content.dto.ContentResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ContentRepository contentRepository;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping
    public Page<ContentAdminDTO> getContent(Pageable pageable) {

        Page<Content> page = contentRepository.findAllWithCreator(pageable);

        return page.map(content -> new ContentAdminDTO(
                content.getId(),
                content.getCreator().getEmail(),
                content.getTitle(),
                content.getStatus().name(),
                content.getCreatedAt()
        ));
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
}

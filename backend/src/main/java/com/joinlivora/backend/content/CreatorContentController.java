package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.ContentResponse;
import com.joinlivora.backend.content.dto.CreateContentRequest;
import com.joinlivora.backend.content.dto.UpdateContentRequest;
import com.joinlivora.backend.service.FileStorageService;
import com.joinlivora.backend.service.StoreResult;
import com.joinlivora.backend.util.UrlUtils;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
@Slf4j
public class CreatorContentController {

    private final ContentService contentService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    @GetMapping("/content")
    public List<ContentResponse> getMyContent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getByEmail(authentication.getName());
        return contentService.getCreatorContent(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/content")
    public ContentResponse createContent(@Valid @RequestBody CreateContentRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getByEmail(authentication.getName());
        
        String thumbnailUrl = request.getThumbnailUrl();
        if (request.getType() == ContentType.VIDEO && thumbnailUrl != null && thumbnailUrl.equals(request.getMediaUrl())) {
            thumbnailUrl = null;
        }

        Content content = Content.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .thumbnailUrl(thumbnailUrl)
                .mediaUrl(request.getMediaUrl())
                .accessLevel(request.getAccessLevel())
                .type(request.getType())
                .unlockPriceTokens(request.getUnlockPriceTokens())
                .build();
        content.setCreator(user);
        Content savedContent = contentService.createContent(content);
        return mapToResponse(savedContent);
    }

    @PostMapping(value = "/content/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ContentResponse> uploadContent(
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String type,
            @RequestParam String accessLevel,
            @RequestParam(required = false) Integer unlockPriceTokens
    ) {
        String fileContentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        boolean isValid = false;
        if ("VIDEO".equals(type) || "CLIP".equals(type)) {
            boolean isValidMime = fileContentType != null && (
                    fileContentType.equals("video/mp4") ||
                    fileContentType.equals("video/quicktime") ||
                    fileContentType.equals("video/webm")
            );
            boolean isValidExt = originalFilename != null && (
                    originalFilename.toLowerCase().endsWith(".mp4") ||
                    originalFilename.toLowerCase().endsWith(".mov") ||
                    originalFilename.toLowerCase().endsWith(".webm")
            );
            isValid = isValidMime && isValidExt;

            if (isValid && "VIDEO".equals(type)) {
                try (InputStream is = file.getInputStream()) {
                    byte[] header = new byte[12];
                    int bytesRead = is.read(header);
                    if (bytesRead < 8) {
                        throw new IllegalArgumentException("Invalid or corrupted video file");
                    }
                    String magic = new String(header, 4, 4, StandardCharsets.US_ASCII);
                    if (!"ftyp".equals(magic)) {
                        throw new IllegalArgumentException("Invalid or corrupted video file");
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not read file for validation", e);
                }
            }
        } else if ("PHOTO".equals(type)) {
            boolean isValidMime = fileContentType != null && (
                    fileContentType.equals("image/jpeg") ||
                    fileContentType.equals("image/png") ||
                    fileContentType.equals("image/webp")
            );
            boolean isValidExt = originalFilename != null && (
                    originalFilename.toLowerCase().endsWith(".jpg") ||
                    originalFilename.toLowerCase().endsWith(".jpeg") ||
                    originalFilename.toLowerCase().endsWith(".png") ||
                    originalFilename.toLowerCase().endsWith(".webp")
            );
            isValid = isValidMime && isValidExt;
        }

        if (!isValid) {
            throw new IllegalArgumentException("Invalid file type for selected content type");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User creator = userService.getByEmail(authentication.getName());
        log.info("CONTROLLER: Uploading content for creator {}: Title={}, Type={}", creator.getEmail(), title, type);

        StoreResult storeResult = fileStorageService.store(file);
        String storedFileUrl = storeResult.getRelativePath();
        String thumbnailUrl = storedFileUrl;

        Content content = Content.builder()
                .title(title)
                .description(description)
                .mediaUrl(storedFileUrl)
                .thumbnailUrl(thumbnailUrl)
                .type(ContentType.valueOf(type))
                .accessLevel(AccessLevel.valueOf(accessLevel))
                .unlockPriceTokens(
                    "PREMIUM".equals(accessLevel) ? unlockPriceTokens : 0
                )
                .build();
        content.setCreator(creator);

        Content savedContent = contentService.createContent(content);
        return ResponseEntity.ok(mapToResponse(savedContent));
    }

    @PutMapping("/content/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ContentResponse updateContent(@PathVariable UUID id, @RequestBody UpdateContentRequest updateRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getByEmail(authentication.getName());
        Content content = contentService.updateContent(id, user.getId(), updateRequest);
        return mapToResponse(content);
    }

    @DeleteMapping("/content/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteContent(@PathVariable UUID id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getByEmail(authentication.getName());
        contentService.deleteContent(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    private ContentResponse mapToResponse(Content content) {
        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(UrlUtils.sanitizeUrl(content.getThumbnailUrl()))
                .mediaUrl(UrlUtils.sanitizeUrl(content.getMediaUrl())) // Creator can see their own media URL or we sign it
                .accessLevel(content.getAccessLevel())
                .type(content.getType())
                .creatorId(content.getCreator().getId())
                .creatorEmail(content.getCreator().getEmail())
                .unlockPriceTokens(content.getUnlockPriceTokens())
                .createdAt(content.getCreatedAt())
                .build();
    }
}

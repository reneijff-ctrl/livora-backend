package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorIdentifierDTO;
import com.joinlivora.backend.creator.dto.CreatorProfileDTO;
import com.joinlivora.backend.creator.dto.UpdateCreatorProfileRequest;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/creator/profile")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CreatorProfileController {

    private final CreatorProfileService creatorProfileService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorProfileDTO> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorProfileService.getMyProfile(principal.getUserId()));
    }

    @GetMapping("/me/identifier")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorIdentifierDTO> getMyIdentifier(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorProfileService.getPublicIdentifier(principal.getUserId()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileDTO> uploadImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type
    ) {
        Long targetUserId = creatorId != null ? creatorId : principal.getUserId();
        return ResponseEntity.ok(creatorProfileService.uploadImage(targetUserId, file, type));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreatorProfileDTO> createProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorProfileService.createProfile(principal.getUserId()));
    }

    @PostMapping("/complete-onboarding")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileDTO> completeOnboarding(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorProfileService.completeOnboarding(principal.getUserId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorProfileDTO> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCreatorProfileRequest request
    ) {
        return ResponseEntity.ok(creatorProfileService.updateProfile(principal.getUserId(), request));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreatorProfileDTO> publishProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorProfileService.publishProfile(principal.getUserId()));
    }
}

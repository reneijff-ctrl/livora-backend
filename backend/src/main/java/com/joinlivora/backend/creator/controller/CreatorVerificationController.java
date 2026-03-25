package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorVerificationRequest;
import com.joinlivora.backend.creator.dto.CreatorVerificationResponse;
import com.joinlivora.backend.creator.dto.CreatorVerificationStatusUpdate;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.service.CreatorVerificationService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.service.FileStorageService;
import com.joinlivora.backend.service.StoreResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CreatorVerificationController {

    private final CreatorVerificationService service;
    private final FileStorageService fileStorageService;

    @PostMapping("/creator/verification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreatorVerificationResponse> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatorVerificationRequest request
    ) {
        CreatorVerification saved = service.createOrResubmit(principal.getUserId(), request);
        CreatorVerificationResponse response = toResponse(saved);
        return ResponseEntity.created(URI.create("/api/creator/verification/" + saved.getId()))
                .body(response);
    }

    @GetMapping("/creator/verification-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreatorVerificationResponse> getVerificationStatus(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.findByCreatorId(principal.getUserId())
                .map(v -> ResponseEntity.ok(toResponse(v)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/creator/verification/upload-id")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> uploadId(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        StoreResult result = fileStorageService.storeCreatorVerificationFile(principal.getUserId(), file, "verification_id");
        service.updateIdImage(principal.getUserId(), result.getRelativePath());
        return ResponseEntity.ok(Map.of("url", result.getRelativePath()));
    }

    @PostMapping("/creator/verification/upload-selfie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> uploadSelfie(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        StoreResult result = fileStorageService.storeCreatorVerificationFile(principal.getUserId(), file, "verification_selfie");
        service.updateSelfieImage(principal.getUserId(), result.getRelativePath());
        return ResponseEntity.ok(Map.of("url", result.getRelativePath()));
    }

    private static CreatorVerificationResponse toResponse(CreatorVerification v) {
        return CreatorVerificationResponse.builder()
                .id(v.getId())
                .creatorId(v.getCreator().getId())
                .legalFirstName(v.getLegalFirstName())
                .legalLastName(v.getLegalLastName())
                .dateOfBirth(v.getDateOfBirth())
                .country(v.getCountry())
                .documentType(v.getDocumentType())
                .idDocumentUrl(v.getIdDocumentUrl())
                .documentBackUrl(v.getDocumentBackUrl())
                .selfieDocumentUrl(v.getSelfieDocumentUrl())
                .status(v.getStatus())
                .rejectionReason(v.getRejectionReason())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}

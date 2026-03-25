package com.joinlivora.backend.admin.controller;

import com.joinlivora.backend.admin.dto.AdminCreatorVerificationResponse;
import com.joinlivora.backend.admin.dto.RejectVerificationRequest;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.service.CreatorVerificationService;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/creator-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreatorVerificationController {
    
    private final CreatorVerificationService creatorVerificationService;
    private final CreatorProfileRepository creatorProfileRepository;

    @GetMapping
    public Page<CreatorVerification> listVerifications(
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        
        return creatorVerificationService.getAllVerifications(status, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCreatorVerificationResponse> getVerification(@PathVariable Long id) {
        CreatorVerification verification = creatorVerificationService.getVerificationById(id);
        return ResponseEntity.ok(mapToAdminDto(verification));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveVerification(@PathVariable Long id) {
        creatorVerificationService.updateStatus(id, VerificationStatus.APPROVED, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectVerification(@PathVariable Long id, @RequestBody RejectVerificationRequest request) {
        creatorVerificationService.updateStatus(id, VerificationStatus.REJECTED, request.getReason());
        return ResponseEntity.ok().build();
    }

    private AdminCreatorVerificationResponse mapToAdminDto(CreatorVerification verification) {
        Long userId = verification.getCreator().getUser().getId();
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId).orElse(null);

        return AdminCreatorVerificationResponse.builder()
                .id(verification.getId())
                .userId(userId)
                .username(verification.getCreator().getUser().getUsername())
                .email(verification.getCreator().getUser().getEmail())
                .legalFirstName(verification.getLegalFirstName())
                .legalLastName(verification.getLegalLastName())
                .dateOfBirth(verification.getDateOfBirth())
                .country(verification.getCountry())
                .bio(profile != null ? profile.getBio() : null)
                .gender(profile != null ? profile.getGender() : null)
                .interestedIn(profile != null ? profile.getInterestedIn() : null)
                .languages(profile != null ? profile.getLanguages() : null)
                .location(profile != null ? profile.getLocation() : null)
                .bodyType(profile != null ? profile.getBodyType() : null)
                .heightCm(profile != null ? profile.getHeightCm() : null)
                .weightKg(profile != null ? profile.getWeightKg() : null)
                .ethnicity(profile != null ? profile.getEthnicity() : null)
                .hairColor(profile != null ? profile.getHairColor() : null)
                .eyeColor(profile != null ? profile.getEyeColor() : null)
                .documentType(verification.getDocumentType().name())
                .idDocumentUrl(verification.getIdDocumentUrl())
                .documentBackUrl(verification.getDocumentBackUrl())
                .selfieDocumentUrl(verification.getSelfieDocumentUrl())
                .status(verification.getStatus())
                .rejectionReason(verification.getRejectionReason())
                .createdAt(verification.getCreatedAt())
                .build();
    }
}

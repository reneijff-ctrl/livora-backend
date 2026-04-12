package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.AdminCreatorDto;
import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.service.CreatorApplicationService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.CreatorVerificationService;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedAdminCreatorService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final CreatorVerificationRepository creatorVerificationRepository;
    private final CreatorApplicationService creatorApplicationService;
    private final CreatorVerificationService creatorVerificationService;
    private final CreatorProfileService creatorProfileService;
    private final AuditService auditService;

    // -------------------------------------------------------------------------
    // Directory — paginated list with optional status filter and search
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_VIEW')")
    public Page<AdminCreatorDto> getCreators(ProfileStatus status, String search, Pageable pageable) {
        Page<CreatorProfile> profiles;

        if (status != null) {
            profiles = creatorProfileRepository.findAll(pageable);
            // Filter in-memory for status (avoids adding a new query method)
            List<AdminCreatorDto> filtered = profiles.getContent().stream()
                    .filter(p -> p.getStatus() == status)
                    .filter(p -> matchesSearch(p, search))
                    .map(this::toDto)
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        profiles = creatorProfileRepository.findAll(pageable);
        List<AdminCreatorDto> dtos = profiles.getContent().stream()
                .filter(p -> matchesSearch(p, search))
                .map(this::toDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, profiles.getTotalElements());
    }

    // -------------------------------------------------------------------------
    // Queue — actionable items only, sorted oldest first
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_VIEW')")
    public List<AdminCreatorDto> getApplicationQueue() {
        Pageable oldest = PageRequest.of(0, 200, Sort.by("submittedAt").ascending());
        return creatorApplicationRepository
                .findByStatus(CreatorApplicationStatus.UNDER_REVIEW, oldest)
                .getContent()
                .stream()
                .map(app -> {
                    Optional<CreatorProfile> profileOpt =
                            creatorProfileRepository.findByUserId(app.getUser().getId());
                    return toDtoFromApplication(app, profileOpt.orElse(null));
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_VIEW')")
    public List<AdminCreatorDto> getVerificationQueue() {
        Pageable oldest = PageRequest.of(0, 200, Sort.by("createdAt").ascending());
        return creatorVerificationRepository
                .findByStatus(VerificationStatus.PENDING, oldest)
                .getContent()
                .stream()
                .map(ver -> {
                    Optional<CreatorProfile> profileOpt =
                            creatorProfileRepository.findByUserId(ver.getCreator().getUser().getId());
                    return toDtoFromVerification(ver, profileOpt.orElse(null));
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Detail — single creator with all data
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_VIEW')")
    public AdminCreatorDto getCreator(Long userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        return toDto(profile);
    }

    // -------------------------------------------------------------------------
    // Lifecycle actions
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_APPROVE')")
    public void approveApplication(Long userId) {
        log.info("Admin approving application for user {}", userId);
        creatorApplicationService.approveApplication(userId);
        auditService.logEvent(null, "CREATOR_APPLICATION_APPROVED", "CREATOR", null, "creatorId:" + userId, null, null);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_REJECT')")
    public void rejectApplication(Long userId, String reason) {
        log.info("Admin rejecting application for user {}", userId);
        creatorApplicationService.rejectApplication(userId, reason);
        auditService.logEvent(null, "CREATOR_APPLICATION_REJECTED", "CREATOR", null, "creatorId:" + userId, null, null);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_APPROVE')")
    public void approveVerification(Long userId) {
        log.info("Admin approving verification for user {}", userId);
        auditService.logEvent(null, "CREATOR_VERIFICATION_APPROVED", "CREATOR", null, "creatorId:" + userId, null, null);
        CreatorVerification ver = creatorVerificationRepository.findByCreator_User_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found for user: " + userId));
        creatorVerificationService.updateStatus(ver.getId(), VerificationStatus.APPROVED, null);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_REJECT')")
    public void rejectVerification(Long userId, String reason) {
        log.info("Admin rejecting verification for user {}", userId);
        auditService.logEvent(null, "CREATOR_VERIFICATION_REJECTED", "CREATOR", null, "creatorId:" + userId, null, null);
        CreatorVerification ver = creatorVerificationRepository.findByCreator_User_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found for user: " + userId));
        creatorVerificationService.updateStatus(ver.getId(), VerificationStatus.REJECTED, reason);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_SUSPEND')")
    public void suspend(Long userId, String reason) {
        log.info("Admin suspending creator user {}: {}", userId, reason);
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        if (profile.getStatus() != ProfileStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE creators can be suspended. Current status: " + profile.getStatus());
        }
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.SUSPENDED);
        auditService.logEvent(null, "CREATOR_SUSPENDED", "CREATOR", null, "creatorId:" + userId, null, null);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_SUSPEND')")
    public void unsuspend(Long userId) {
        log.info("Admin unsuspending creator user {}", userId);
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        if (profile.getStatus() != ProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED creators can be unsuspended. Current status: " + profile.getStatus());
        }
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.ACTIVE);
        auditService.logEvent(null, "CREATOR_UNSUSPENDED", "CREATOR", null, "creatorId:" + userId, null, null);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_APPROVE')")
    public void approveCreator(Long userId) {
        log.info("Admin approving creator profile for user {}", userId);
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        if (profile.getStatus() != ProfileStatus.PENDING) {
            throw new IllegalStateException("Only PENDING creators can be approved. Current status: " + profile.getStatus());
        }
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.ACTIVE);
        creatorVerificationRepository.findByCreator_User_Id(userId).ifPresent(ver ->
                creatorVerificationService.updateStatus(ver.getId(), VerificationStatus.APPROVED, null));
        auditService.logEvent(null, "CREATOR_APPROVED", "CREATOR", null, "creatorId:" + userId, null, null);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_REJECT')")
    public void rejectCreator(Long userId) {
        log.info("Admin rejecting creator profile for user {}", userId);
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.DRAFT);
        creatorVerificationRepository.findByCreator_User_Id(userId).ifPresent(ver ->
                creatorVerificationService.updateStatus(ver.getId(), VerificationStatus.REJECTED, "Rejected by admin"));
        auditService.logEvent(null, "CREATOR_REJECTED", "CREATOR", null, "creatorId:" + userId, null, null);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_APPROVE')")
    public void activateCreator(Long userId) {
        log.info("Admin activating creator profile for user {}", userId);
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.ACTIVE);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('CREATOR_SUSPEND')")
    public void suspendCreator(Long userId, String reason) {
        log.info("Admin suspending creator profile for user {}: {}", userId, reason);
        auditService.logEvent(null, "CREATOR_SUSPENDED", "CREATOR", null, "creatorId:" + userId, null, null);
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));
        if (profile.getStatus() != ProfileStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE creators can be suspended. Current status: " + profile.getStatus());
        }
        creatorProfileService.updateCreatorStatus(userId, ProfileStatus.SUSPENDED);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private AdminCreatorDto toDto(CreatorProfile profile) {
        Long userId = profile.getUser().getId();
        Optional<CreatorApplication> appOpt = creatorApplicationRepository.findByUserId(userId);
        Optional<CreatorVerification> verOpt = creatorVerificationRepository.findByCreator_User_Id(userId);

        AdminCreatorDto.AdminCreatorDtoBuilder builder = AdminCreatorDto.builder()
                .userId(userId)
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .email(profile.getUser().getEmail())
                .profileImage(profile.getAvatarUrl())
                .status(profile.getStatus())
                .gender(profile.getGender())
                .birthDate(profile.getBirthDate())
                .interestedIn(profile.getInterestedIn())
                .languages(profile.getLanguages());

        appOpt.ifPresent(app -> builder
                .applicationId(app.getId())
                .applicationStatus(app.getStatus().name())
                .applicationSubmittedAt(app.getSubmittedAt())
                .applicationApprovedAt(app.getApprovedAt())
                .applicationReviewNotes(app.getReviewNotes())
                .termsAccepted(app.isTermsAccepted())
                .ageVerified(app.isAgeVerified()));

        verOpt.ifPresent(ver -> builder
                .verificationId(ver.getId())
                .verificationStatus(ver.getStatus())
                .verificationSubmittedAt(ver.getCreatedAt())
                .verificationRejectionReason(ver.getRejectionReason())
                .legalFirstName(ver.getLegalFirstName())
                .legalLastName(ver.getLegalLastName())
                .country(ver.getCountry())
                .idDocumentUrl(ver.getIdDocumentUrl())
                .documentBackUrl(ver.getDocumentBackUrl())
                .selfieDocumentUrl(ver.getSelfieDocumentUrl()));

        return builder.build();
    }

    private AdminCreatorDto toDtoFromApplication(CreatorApplication app, CreatorProfile profile) {
        Optional<CreatorVerification> verOpt = creatorVerificationRepository
                .findByCreator_User_Id(app.getUser().getId());

        AdminCreatorDto.AdminCreatorDtoBuilder builder = AdminCreatorDto.builder()
                .userId(app.getUser().getId())
                .username(app.getUser().getUsername())
                .displayName(profile != null ? profile.getDisplayName() : app.getUser().getUsername())
                .email(app.getUser().getEmail())
                .status(profile != null ? profile.getStatus() : ProfileStatus.DRAFT)
                .profileImage(profile != null ? profile.getAvatarUrl() : null)
                .gender(profile != null ? profile.getGender() : null)
                .birthDate(profile != null ? profile.getBirthDate() : null)
                .interestedIn(profile != null ? profile.getInterestedIn() : null)
                .languages(profile != null ? profile.getLanguages() : null)
                .applicationId(app.getId())
                .applicationStatus(app.getStatus().name())
                .applicationSubmittedAt(app.getSubmittedAt())
                .applicationApprovedAt(app.getApprovedAt())
                .applicationReviewNotes(app.getReviewNotes())
                .termsAccepted(app.isTermsAccepted())
                .ageVerified(app.isAgeVerified());

        verOpt.ifPresent(ver -> builder
                .verificationId(ver.getId())
                .verificationStatus(ver.getStatus())
                .verificationSubmittedAt(ver.getCreatedAt())
                .verificationRejectionReason(ver.getRejectionReason())
                .legalFirstName(ver.getLegalFirstName())
                .legalLastName(ver.getLegalLastName())
                .country(ver.getCountry())
                .idDocumentUrl(ver.getIdDocumentUrl())
                .documentBackUrl(ver.getDocumentBackUrl())
                .selfieDocumentUrl(ver.getSelfieDocumentUrl()));

        return builder.build();
    }

    private AdminCreatorDto toDtoFromVerification(CreatorVerification ver, CreatorProfile profile) {
        Long userId = ver.getCreator().getUser().getId();
        Optional<CreatorApplication> appOpt = creatorApplicationRepository.findByUserId(userId);

        AdminCreatorDto.AdminCreatorDtoBuilder builder = AdminCreatorDto.builder()
                .userId(userId)
                .username(ver.getCreator().getUser().getUsername())
                .displayName(profile != null ? profile.getDisplayName() : ver.getCreator().getUser().getUsername())
                .email(ver.getCreator().getUser().getEmail())
                .status(profile != null ? profile.getStatus() : ProfileStatus.DRAFT)
                .profileImage(profile != null ? profile.getAvatarUrl() : null)
                .gender(profile != null ? profile.getGender() : null)
                .birthDate(profile != null ? profile.getBirthDate() : null)
                .interestedIn(profile != null ? profile.getInterestedIn() : null)
                .languages(profile != null ? profile.getLanguages() : null)
                .verificationId(ver.getId())
                .verificationStatus(ver.getStatus())
                .verificationSubmittedAt(ver.getCreatedAt())
                .verificationRejectionReason(ver.getRejectionReason())
                .legalFirstName(ver.getLegalFirstName())
                .legalLastName(ver.getLegalLastName())
                .country(ver.getCountry())
                .idDocumentUrl(ver.getIdDocumentUrl())
                .documentBackUrl(ver.getDocumentBackUrl())
                .selfieDocumentUrl(ver.getSelfieDocumentUrl());

        appOpt.ifPresent(app -> builder
                .applicationId(app.getId())
                .applicationStatus(app.getStatus().name())
                .applicationSubmittedAt(app.getSubmittedAt())
                .applicationApprovedAt(app.getApprovedAt())
                .applicationReviewNotes(app.getReviewNotes())
                .termsAccepted(app.isTermsAccepted())
                .ageVerified(app.isAgeVerified()));

        return builder.build();
    }

    private boolean matchesSearch(CreatorProfile profile, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        return (profile.getUsername() != null && profile.getUsername().toLowerCase().contains(q))
                || (profile.getDisplayName() != null && profile.getDisplayName().toLowerCase().contains(q))
                || (profile.getUser() != null && profile.getUser().getEmail() != null
                    && profile.getUser().getEmail().toLowerCase().contains(q));
    }
}

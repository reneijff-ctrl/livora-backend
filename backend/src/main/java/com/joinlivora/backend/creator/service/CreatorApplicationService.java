package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.admin.dto.AdminCreatorApplicationResponse;
import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreatorApplicationService {

    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final CreatorProfileService creatorProfileService;
    private final AdminRealtimeEventService adminRealtimeEventService;

    @Transactional
    public CreatorApplication startApplication(Long userId) {
        return creatorApplicationRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                    CreatorApplication application = CreatorApplication.builder()
                            .user(user)
                            .status(CreatorApplicationStatus.PENDING)
                            .build();
                    return creatorApplicationRepository.save(application);
                });
    }

    @Transactional
    public CreatorApplication submitApplication(Long userId, boolean termsAccepted, boolean ageVerified) {
        if (!termsAccepted) {
            throw new IllegalArgumentException("Terms must be accepted to submit application");
        }
        if (!ageVerified) {
            throw new IllegalArgumentException("Age must be verified to submit application");
        }

        CreatorApplication application = creatorApplicationRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator application not found for user: " + userId));

        application.setTermsAccepted(true);
        application.setAgeVerified(true);
        application.setStatus(CreatorApplicationStatus.UNDER_REVIEW);

        CreatorApplication saved = creatorApplicationRepository.save(application);
        adminRealtimeEventService.broadcastCreatorApplication(saved);
        return saved;
    }

    @Transactional
    public CreatorApplication approveApplication(Long userId) {
        CreatorApplication application = creatorApplicationRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator application not found for user: " + userId));

        application.setStatus(CreatorApplicationStatus.APPROVED);
        application.setApprovedAt(LocalDateTime.now());
        creatorApplicationRepository.save(application);

        User user = application.getUser();
        userService.upgradeToCreator(user.getEmail());
        
        // userService.upgradeToCreator(user.getEmail()) will also call
        // creatorProfileService.initializeCreatorProfile(user)
        
        return application;
    }

    @Transactional
    public void approveApplicationById(Long id) {
        CreatorApplication application = creatorApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Creator application not found: " + id));
        
        approveApplication(application.getUser().getId());
    }

    @Transactional
    public CreatorApplication rejectApplication(Long userId, String reviewNotes) {
        CreatorApplication application = creatorApplicationRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator application not found for user: " + userId));

        application.setStatus(CreatorApplicationStatus.REJECTED);
        application.setReviewNotes(reviewNotes);

        return creatorApplicationRepository.save(application);
    }

    @Transactional
    public void rejectApplicationById(Long id, String reviewNotes) {
        CreatorApplication application = creatorApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Creator application not found: " + id));
        
        rejectApplication(application.getUser().getId(), reviewNotes);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorApplication> getApplication(Long userId) {
        return creatorApplicationRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<CreatorApplication> getApplications(Pageable pageable, CreatorApplicationStatus status) {
        if (status != null) {
            return creatorApplicationRepository.findByStatus(status, pageable);
        } else {
            return creatorApplicationRepository.findAll(pageable);
        }
    }

    public AdminCreatorApplicationResponse mapToAdminDto(CreatorApplication application) {
        User user = application.getUser();
        return AdminCreatorApplicationResponse.builder()
                .id(application.getId())
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : null)
                .email(user != null ? user.getEmail() : null)
                .status(application.getStatus().name())
                .termsAccepted(application.isTermsAccepted())
                .ageVerified(application.isAgeVerified())
                .submittedAt(application.getSubmittedAt())
                .approvedAt(application.getApprovedAt())
                .reviewNotes(application.getReviewNotes())
                .build();
    }
}

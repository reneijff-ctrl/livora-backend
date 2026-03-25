package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.onboarding.Step1BasicsRequest;
import com.joinlivora.backend.creator.dto.onboarding.Step2ProfileRequest;
import com.joinlivora.backend.creator.dto.onboarding.Step3VerificationRequest;
import com.joinlivora.backend.creator.model.*;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorOnboardingService {

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorRepository creatorRepository;
    private final CreatorVerificationRepository creatorVerificationRepository;
    private final CreatorProfileService creatorProfileService;

    @Transactional
    public void saveStep1Basics(Long userId, Step1BasicsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername().trim().toLowerCase());
        }
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        userRepository.save(user);

        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CreatorProfile newProfile = CreatorProfile.builder()
                            .user(user)
                            .username(user.getUsername())
                            .displayName(user.getDisplayName())
                            .status(ProfileStatus.DRAFT)
                            .visibility(ProfileVisibility.PRIVATE)
                            .build();
                    return creatorProfileRepository.save(newProfile);
                });

        if (request.getBio() != null) {
            profile.setBio(request.getBio().trim());
        }
        if (request.getProfilePicture() != null) {
            profile.setAvatarUrl(request.getProfilePicture().trim());
        }
        creatorProfileRepository.save(profile);
    }

    @Transactional
    public void saveStep2Profile(Long userId, Step2ProfileRequest request) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId + ". Complete step 1 first."));

        profile.setRealName(request.getRealName());
        profile.setBirthDate(request.getBirthDate());
        profile.setGender(request.getGender());
        profile.setInterestedIn(request.getInterestedIn());
        profile.setLanguages(request.getLanguages());
        profile.setLocation(request.getLocation());
        profile.setBodyType(request.getBodyType());
        profile.setHeightCm(request.getHeightCm());
        profile.setWeightKg(request.getWeightKg());
        profile.setEthnicity(request.getEthnicity());
        profile.setHairColor(request.getHairColor());
        profile.setEyeColor(request.getEyeColor());

        creatorProfileRepository.save(profile);
    }

    @Transactional
    public void saveStep3Verification(Long userId, Step3VerificationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found. Complete previous steps first."));

        // Ensure Creator record exists for Verification
        Creator creator = creatorRepository.findByUser_Id(userId)
                .orElseGet(() -> creatorRepository.save(
                        Creator.builder()
                                .user(user)
                                .bio(profile.getBio())
                                .profileImageUrl(profile.getAvatarUrl())
                                .active(false)
                                .build()
                ));

        CreatorVerification verification = creatorVerificationRepository.findByCreator_User_Id(userId)
                .orElseGet(() -> CreatorVerification.builder()
                        .creator(creator)
                        .status(VerificationStatus.PENDING)
                        .build());

        // Map fields from profile to verification if they match
        verification.setLegalFirstName(profile.getRealName() != null ? profile.getRealName() : "Unknown");
        verification.setLegalLastName(""); // We might only have realName as one string
        verification.setDateOfBirth(profile.getBirthDate() != null ? profile.getBirthDate() : java.time.LocalDate.now().minusYears(18));
        verification.setCountry(profile.getLocation() != null ? profile.getLocation() : "Unknown");
        verification.setDocumentType(DocumentType.ID_CARD); // Default

        verification.setIdDocumentUrl(request.getGovernmentIdImage() != null ? request.getGovernmentIdImage() : "");
        verification.setSelfieDocumentUrl(request.getSelfieWithId() != null ? request.getSelfieWithId() : "");
        verification.setStatus(VerificationStatus.UNDER_REVIEW);

        creatorVerificationRepository.save(verification);
    }

    @Transactional(readOnly = true)
    public boolean checkStep4Payout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        return user.getStripeAccountId() != null && user.isStripeOnboardingComplete();
    }
}

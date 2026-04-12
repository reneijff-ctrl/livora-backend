package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatorDto {

    // Core identity
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private String profileImage;

    // Lifecycle status (from CreatorProfile)
    private ProfileStatus status;

    // Application data
    private Long applicationId;
    private String applicationStatus;
    private LocalDateTime applicationSubmittedAt;
    private LocalDateTime applicationApprovedAt;
    private String applicationReviewNotes;
    private Boolean termsAccepted;
    private Boolean ageVerified;

    // Verification data
    private Long verificationId;
    private VerificationStatus verificationStatus;
    private Instant verificationSubmittedAt;
    private String verificationRejectionReason;
    private String legalFirstName;
    private String legalLastName;
    private String country;
    // Onboarding profile fields
    private String gender;
    private LocalDate birthDate;
    private String interestedIn;
    private String languages;
    private String idDocumentUrl;
    private String documentBackUrl;
    private String selfieDocumentUrl;
}

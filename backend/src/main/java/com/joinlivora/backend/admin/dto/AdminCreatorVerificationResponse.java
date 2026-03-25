package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.creator.verification.VerificationStatus;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatorVerificationResponse {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String legalFirstName;
    private String legalLastName;
    private LocalDate dateOfBirth;
    private String country;
    private String bio;
    private String gender;
    private String interestedIn;
    private String languages;
    private String location;
    private String bodyType;
    private Integer heightCm;
    private Integer weightKg;
    private String ethnicity;
    private String hairColor;
    private String eyeColor;
    private String documentType;
    private String idDocumentUrl;
    private String documentBackUrl;
    private String selfieDocumentUrl;
    private VerificationStatus status;
    private String rejectionReason;
    private Instant createdAt;
}

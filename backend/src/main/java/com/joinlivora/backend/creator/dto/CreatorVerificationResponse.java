package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.DocumentType;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorVerificationResponse {
    private Long id;
    private Long creatorId;
    private String legalFirstName;
    private String legalLastName;
    private LocalDate dateOfBirth;
    private String country;
    private DocumentType documentType;
    private String idDocumentUrl;
    private String documentBackUrl;
    private String selfieDocumentUrl;
    private VerificationStatus status;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}

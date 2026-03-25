package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorVerificationRequest {

    @NotBlank(message = "Legal first name is required")
    private String legalFirstName;

    @NotBlank(message = "Legal last name is required")
    private String legalLastName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Country is required")
    private String country;

    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotBlank(message = "ID document URL is required")
    private String idDocumentUrl;

    private String documentBackUrl;

    @NotBlank(message = "Selfie document URL is required")
    private String selfieDocumentUrl;
}

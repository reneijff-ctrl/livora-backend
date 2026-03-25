package com.joinlivora.backend.creator.model;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "creator_verification", indexes = {
    @Index(name = "idx_creator_verification_status", columnList = "status"),
    @Index(name = "idx_creator_verification_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false, unique = true)
    private Creator creator;

    @Column(nullable = false)
    private String legalFirstName;

    @Column(nullable = false)
    private String legalLastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(name = "id_document_url", nullable = false)
    private String idDocumentUrl;

    @Column(name = "document_back_url")
    private String documentBackUrl;

    @Column(name = "selfie_document_url", nullable = false)
    private String selfieDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = VerificationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

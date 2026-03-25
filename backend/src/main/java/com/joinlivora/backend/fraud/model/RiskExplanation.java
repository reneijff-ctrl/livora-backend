package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "risk_explanations", indexes = {
    @Index(name = "idx_risk_explanation_subject", columnList = "subject_type, subject_id"),
    @Index(name = "idx_risk_explanation_generated_at", columnList = "generated_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskExplanation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private RiskSubjectType subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskDecision decision;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "explanation_text", columnDefinition = "TEXT")
    private String explanationText;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> factors;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}

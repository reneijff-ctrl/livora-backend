package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_decision_audits", indexes = {
    @Index(name = "idx_risk_audit_user", columnList = "user_id"),
    @Index(name = "idx_risk_audit_transaction", columnList = "transaction_id"),
    @Index(name = "idx_risk_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecisionAudit {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "decision_type", nullable = false, updatable = false)
    private String decisionType; // e.g., FRAUD_EVALUATION, CHARGEBACK_ENFORCEMENT

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, updatable = false)
    private RiskLevel riskLevel;

    @Column(name = "score", updatable = false)
    private Integer score;

    @Column(name = "triggered_by", nullable = false, updatable = false)
    private String triggeredBy; // e.g., SYSTEM, STRIPE_WEBHOOK, ADMIN_ID

    @Column(name = "actions_taken", columnDefinition = "TEXT", updatable = false)
    private String actionsTaken;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "metadata", columnDefinition = "TEXT", updatable = false)
    private String metadata; // JSON for extensible data

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

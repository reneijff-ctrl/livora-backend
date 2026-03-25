package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_fraud_signals", indexes = {
    @Index(name = "idx_rule_fraud_signal_user_id", columnList = "user_id"),
    @Index(name = "idx_rule_fraud_signal_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleFraudSignal {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "creator_id", updatable = false)
    private Long creatorId;

    @Column(updatable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, updatable = false)
    private FraudDecisionLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FraudSignalType type;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FraudSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "action_reason")
    private String actionReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
